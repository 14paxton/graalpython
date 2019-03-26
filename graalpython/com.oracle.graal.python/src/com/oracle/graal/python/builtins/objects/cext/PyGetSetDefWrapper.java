/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyGetSetDef}.
 */
@ExportLibrary(InteropLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class PyGetSetDefWrapper extends PythonNativeWrapper {
    public static final String NAME = "name";
    public static final String DOC = "doc";

    public PyGetSetDefWrapper(PythonObject delegate) {
        super(delegate);
    }

    static boolean isInstance(TruffleObject o) {
        return o instanceof PyGetSetDefWrapper;
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case NAME:
            case DOC:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Exclusive @Cached ReadFieldNode readFieldNode) {
        return readFieldNode.execute(this.getDelegate(), member);
    }

    @ImportStatic({SpecialMethodNames.class, PyGetSetDefWrapper.class})
    @GenerateUncached
    abstract static class ReadFieldNode extends Node {
        public abstract Object execute(Object delegate, String key);

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(NAME, key)"})
        Object getName(PythonObject object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        // TODO TRUFFLE LIBRARY MIGRATION: is 'allowUncached = true' safe ?
                        @Shared("getAttrNode") @Cached(value = "create(__GETATTRIBUTE__)", allowUncached = true) LookupAndCallBinaryNode getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("asCharPointerNode") @Cached CExtNodes.AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.executeObject(object, __NAME__);
            if (doc == PNone.NONE) {
                return toSulongNode.execute(PNone.NO_VALUE);
            } else {
                return asCharPointerNode.execute(doc);
            }
        }

        @Specialization(guards = {"eq(DOC, key)"})
        Object getDoc(PythonObject object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        // TODO TRUFFLE LIBRARY MIGRATION: is 'allowUncached = true' safe ?
                        @Shared("getAttrNode") @Cached(value = "create(__GETATTRIBUTE__)", allowUncached = true) LookupAndCallBinaryNode getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("asCharPointerNode") @Cached CExtNodes.AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.executeObject(object, __DOC__);
            if (doc == PNone.NONE) {
                return toSulongNode.execute(PNone.NO_VALUE);
            } else {
                return asCharPointerNode.execute(doc);
            }
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return DOC.equals(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(String member) {
        return DOC.equals(member);
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @Cached("create(__SETATTR__)") LookupAndCallTernaryNode setAttrNode,
                    @Exclusive @Cached CExtNodes.FromCharPointerNode fromCharPointerNode) throws UnsupportedMessageException {
        if (!DOC.equals(member)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        setAttrNode.execute(getDelegate(), SpecialAttributeNames.__DOC__, fromCharPointerNode.execute(value));
    }

    @ExportMessage
    protected boolean isMemberRemovable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void removeMember(@SuppressWarnings("unused") String member) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}
