/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(PythonObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class PBuiltinFunction extends PythonBuiltinObject implements BoundBuiltinCallable<PBuiltinFunction> {

    private final String name;
    private final String qualname;
    private final Object enclosingType;
    private final RootCallTarget callTarget;
    private final Signature signature;
    @CompilationFinal(dimensions = 1) private final Object[] defaults;
    @CompilationFinal(dimensions = 1) private final PKeyword[] kwDefaults;

    public PBuiltinFunction(PythonLanguage lang, String name, Object enclosingType, int numDefaults, RootCallTarget callTarget) {
        this(lang, name, enclosingType, generateDefaults(numDefaults), null, callTarget);
    }

    public PBuiltinFunction(PythonLanguage lang, String name, Object enclosingType, Object[] defaults, PKeyword[] kwDefaults, RootCallTarget callTarget) {
        super(PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.PBuiltinFunction.getInstanceShape(lang));
        this.name = name;
        if (enclosingType != null) {
            this.qualname = PString.cat(GetNameNode.doSlowPath(enclosingType), ".", name);
        } else {
            this.qualname = name;
        }
        this.enclosingType = enclosingType;
        this.callTarget = callTarget;
        this.signature = ((PRootNode) callTarget.getRootNode()).getSignature();
        this.defaults = defaults;
        this.kwDefaults = kwDefaults != null ? kwDefaults : generateKwDefaults(signature);
    }

    private static PKeyword[] generateKwDefaults(Signature signature) {
        String[] keywordNames = signature.getKeywordNames();
        PKeyword[] kwDefaults = new PKeyword[keywordNames.length];
        for (int i = 0; i < keywordNames.length; i++) {
            kwDefaults[i] = new PKeyword(keywordNames[i], PNone.NO_VALUE);
        }
        return kwDefaults;
    }

    private static Object[] generateDefaults(int numDefaults) {
        Object[] defaults = new Object[numDefaults];
        Arrays.fill(defaults, PNone.NO_VALUE);
        return defaults;
    }

    public RootNode getFunctionRootNode() {
        return callTarget.getRootNode();
    }

    public NodeFactory<? extends PythonBuiltinBaseNode> getBuiltinNodeFactory() {
        RootNode functionRootNode = getFunctionRootNode();
        if (functionRootNode instanceof BuiltinFunctionRootNode) {
            return ((BuiltinFunctionRootNode) functionRootNode).getFactory();
        } else {
            return null;
        }
    }

    public boolean isReverseOperationSlot() {
        return isReverseOperationSlot(callTarget);
    }

    public static boolean isReverseOperationSlot(RootCallTarget ct) {
        RootNode functionRootNode = ct.getRootNode();
        if (functionRootNode instanceof BuiltinFunctionRootNode) {
            return ((BuiltinFunctionRootNode) functionRootNode).getBuiltin().reverseOperation();
        } else {
            return false;
        }
    }

    public Class<? extends PythonBuiltinBaseNode> getNodeClass() {
        return getBuiltinNodeFactory() != null ? getBuiltinNodeFactory().getNodeClass() : null;
    }

    public Signature getSignature() {
        return signature;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    public String getName() {
        return name;
    }

    public String getQualname() {
        return qualname;
    }

    public Object getEnclosingType() {
        return enclosingType;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PBuiltinFunction %s at 0x%x", qualname, hashCode());
    }

    public PBuiltinFunction boundToObject(PythonBuiltinClassType klass, PythonObjectFactory factory) {
        if (klass == enclosingType) {
            return this;
        } else {
            PBuiltinFunction func = factory.createBuiltinFunction(name, klass, defaults.length, callTarget);
            func.setAttribute(__DOC__, getAttribute(__DOC__));
            return func;
        }
    }

    public Object[] getDefaults() {
        return defaults;
    }

    public PKeyword[] getKwDefaults() {
        return kwDefaults;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isCallable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    String getExecutableName() {
        return getName();
    }

    @Override
    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getLazyPythonClass() {
        return PythonBuiltinClassType.PBuiltinFunction;
    }

    @ExportMessage
    // Note: Avoiding calling __get__ for builtin functions seems like just an optimization, but it
    // is actually necessary for being able to correctly call special methods on None, because
    // type(None).__eq__.__get__(None, type(None)) wouldn't bind the method correctly
    public Object callUnboundMethodWithState(ThreadState state, Object receiver, Object[] arguments,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Shared("callMethod") @Cached CallUnboundMethodNode call) {
        VirtualFrame frame = null;
        if (gotState.profile(state != null)) {
            frame = PArguments.frameForCall(state);
        }
        return call.execute(frame, this, receiver, arguments);
    }

    @ExportMessage
    public Object callUnboundMethodIgnoreGetExceptionWithState(ThreadState state, Object receiver, Object[] arguments,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Shared("callMethod") @Cached CallUnboundMethodNode call) {
        return callUnboundMethodWithState(state, receiver, arguments, gotState, call);
    }

    @GenerateUncached
    public abstract static class CallUnboundMethodNode extends Node {
        public abstract Object execute(Frame frame, PBuiltinFunction method, Object receiver, Object[] arguments);

        @Specialization(guards = "arguments.length == 0")
        static Object unary(VirtualFrame frame, PBuiltinFunction method, Object receiver, @SuppressWarnings("unused") Object[] arguments,
                        @Cached CallUnaryMethodNode callNode) {
            return callNode.executeObject(frame, method, receiver);
        }

        @Specialization(guards = "arguments.length == 1")
        static Object binary(VirtualFrame frame, PBuiltinFunction method, Object receiver, Object[] arguments,
                        @Cached CallBinaryMethodNode callNode) {
            return callNode.executeObject(frame, method, receiver, arguments[0]);
        }

        @Specialization(guards = "arguments.length == 2")
        static Object ternary(VirtualFrame frame, PBuiltinFunction method, Object receiver, Object[] arguments,
                        @Cached CallTernaryMethodNode callNode) {
            return callNode.execute(frame, method, receiver, arguments[0], arguments[1]);
        }

        @Specialization(guards = "arguments.length == 3")
        static Object quaternary(VirtualFrame frame, PBuiltinFunction method, Object receiver, Object[] arguments,
                        @Cached CallQuaternaryMethodNode callNode) {
            return callNode.execute(frame, method, receiver, arguments[0], arguments[1], arguments[2]);
        }

        @Specialization(replaces = {"unary", "binary", "ternary", "quaternary"})
        static Object generic(VirtualFrame frame, PBuiltinFunction method, Object receiver, Object[] arguments,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, method, PositionalArgumentsNode.prependArgument(receiver, arguments));
        }
    }
}
