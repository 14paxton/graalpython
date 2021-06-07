/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic({PGuards.class, PythonOptions.class, SpecialMethodNames.class, SpecialAttributeNames.class, BuiltinNames.class})
public abstract class PythonBuiltinBaseNode extends PNodeWithRaise implements IndirectCallNode {
    @Child private PythonObjectFactory objectFactory;
    @Child private PConstructAndRaiseNode constructAndRaiseNode;
    @CompilationFinal private ContextReference<PythonContext> contextRef;
    private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
    private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

    @Override
    public Assumption needNotPassFrameAssumption() {
        return dontNeedCallerFrame;
    }

    @Override
    public Assumption needNotPassExceptionAssumption() {
        return dontNeedExceptionState;
    }

    protected final PythonObjectFactory factory() {
        if (objectFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isAdoptable()) {
                objectFactory = insert(PythonObjectFactory.create());
            } else {
                objectFactory = getCore().factory();
            }
        }
        return objectFactory;
    }

    public final PConstructAndRaiseNode getConstructAndRaiseNode() {
        if (constructAndRaiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            constructAndRaiseNode = insert(PConstructAndRaiseNode.create());
        }
        return constructAndRaiseNode;
    }

    public final Python3Core getCore() {
        return getContext().getCore();
    }

    public final Object getPythonClass(Object lazyClass, ConditionProfile profile) {
        if (profile.profile(lazyClass instanceof PythonBuiltinClassType)) {
            return getCore().lookupType((PythonBuiltinClassType) lazyClass);
        } else {
            return lazyClass;
        }
    }

    protected final ContextReference<PythonContext> getContextRef() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef;
    }

    public final PythonContext getContext() {
        return getContextRef().get();
    }

    public final Object getPosixSupport() {
        return getContext().getPosixSupport();
    }

    public final PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e) {
        return getConstructAndRaiseNode().raiseOSError(frame, e.getErrorCode(), e.getMessage(), null, null);
    }

    public final PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e, Object filename1) {
        return getConstructAndRaiseNode().raiseOSError(frame, e.getErrorCode(), e.getMessage(), filename1, null);
    }

    public final PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e, Object filename1, Object filename2) {
        return getConstructAndRaiseNode().raiseOSError(frame, e.getErrorCode(), e.getMessage(), filename1, filename2);
    }

    public final PException raiseOSError(VirtualFrame frame, int code, String message) {
        return getConstructAndRaiseNode().raiseOSError(frame, code, message, null, null);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum num) {
        return getConstructAndRaiseNode().raiseOSError(frame, num);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, Exception e) {
        return getConstructAndRaiseNode().raiseOSError(frame, oserror, e);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename) {
        return getConstructAndRaiseNode().raiseOSError(frame, oserror, filename);
    }

    public final PException raiseOSError(VirtualFrame frame, Exception e) {
        return getConstructAndRaiseNode().raiseOSError(frame, e);
    }

    public final PException raiseOSError(VirtualFrame frame, Exception e, String filename) {
        return getConstructAndRaiseNode().raiseOSError(frame, e, filename);
    }

    public final PException raiseOSError(VirtualFrame frame, Exception e, String filename, String filename2) {
        return getConstructAndRaiseNode().raiseOSError(frame, e, filename, filename2);
    }
}
