/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SPEC__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

public class ImportFromNode extends AbstractImportNode {
    @Children private final WriteNode[] aslist;
    @Child private GetItemNode getItem;
    @Child private CastToJavaStringNode castToJavaStringNode;

    private final String importee;
    private final int level;

    @Child private IsBuiltinClassProfile getAttrErrorProfile = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile getFileErrorProfile = IsBuiltinClassProfile.create();
    @CompilationFinal(dimensions = 1) private final String[] fromlist;

    public static ImportFromNode create(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        return new ImportFromNode(importee, fromlist, readNodes, level);
    }

    public String getImportee() {
        return importee;
    }

    public int getLevel() {
        return level;
    }

    public String[] getFromlist() {
        return fromlist;
    }

    protected ImportFromNode(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        this.importee = importee;
        this.fromlist = fromlist;
        this.aslist = readNodes;
        this.level = level;
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    public void executeVoid(VirtualFrame frame) {
        Object globals = PArguments.getGlobals(frame);
        Object importedModule = importModule(frame, importee, globals, fromlist, level);
        PythonObjectLibrary pol = ensurePythonLibrary();
        Object sysModules = getSysModules(frame, pol);

        for (int i = 0; i < fromlist.length; i++) {
            String attr = fromlist[i];
            WriteNode writeNode = aslist[i];
            try {
                writeNode.executeObject(frame, pol.lookupAttributeStrict(importedModule, frame, attr));
            } catch (PException pe) {
                pe.expectAttributeError(getAttrErrorProfile);
                Object moduleName = "<unknown module name>";
                try {
                    moduleName = pol.lookupAttributeStrict(importedModule, frame, __NAME__);
                    try {
                        String pkgname = ensureCastToStringNode().execute(moduleName);
                        String fullname = PString.cat(pkgname, ".", attr);
                        writeNode.executeObject(frame, ensureGetItemNode().execute(frame, sysModules, fullname));
                    } catch (CannotCastException cce) {
                        throw pe;
                    }
                } catch (PException pe2) {
                    Object modulePath = "unknown location";
                    if (!getAttrErrorProfile.profileException(pe2, PythonBuiltinClassType.AttributeError)) {
                        try {
                            modulePath = pol.lookupAttributeStrict(importedModule, frame, __FILE__);
                        } catch (PException pe3) {
                            pe3.expectAttributeError(getFileErrorProfile);
                        }
                    }

                    if (isModuleInitialising(frame, pol, importedModule)) {
                        throw raiseImportError(frame, moduleName, modulePath, ErrorMessages.CANNOT_IMPORT_NAME_CIRCULAR, attr, moduleName);
                    } else {
                        throw raiseImportError(frame, moduleName, modulePath, ErrorMessages.CANNOT_IMPORT_NAME, attr, moduleName, modulePath);
                    }
                }
            }
        }
    }

    private Object getSysModules(VirtualFrame frame, PythonObjectLibrary pol) {
        Object sysModules = getContext().getSysModules();
        if (sysModules == null) {
            PythonModule sys = getContext().getCore().lookupBuiltinModule("sys");
            sysModules = pol.lookupAttribute(sys, frame, "modules");
        }
        assert sysModules != PNone.NO_VALUE : "ImportFromNode: sys.modules was not found!";
        return sysModules;
    }

    private static boolean isModuleInitialising(VirtualFrame frame, PythonObjectLibrary pol, Object importedModule) {
        Object spec = pol.lookupAttribute(importedModule, frame, __SPEC__);
        if (spec != PNone.NO_VALUE) {
            Object initializing = pol.lookupAttribute(spec, frame, "_initializing");
            return pol.isTrue(initializing);
        }
        return false;
    }

    private GetItemNode ensureGetItemNode() {
        if (getItem == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getItem = insert(GetItemNode.create());
        }
        return getItem;
    }

    private CastToJavaStringNode ensureCastToStringNode() {
        if (castToJavaStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castToJavaStringNode = insert(CastToJavaStringNode.create());
        }
        return castToJavaStringNode;
    }
}
