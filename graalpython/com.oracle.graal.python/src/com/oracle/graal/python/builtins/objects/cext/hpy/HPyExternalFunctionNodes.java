/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_STRING_ARRAY;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyFuncSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseArgHandlesNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySSizeArgFuncToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySSizeObjArgProcToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyVarargsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyArrayWrappers.HPyArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckPrimitiveResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HPyExternalFunctionNodes {

    private static final String KW_CALLABLE = "$callable";
    private static final String[] KEYWORDS_HIDDEN_CALLABLE = new String[]{KW_CALLABLE};

    /**
     * Creates a built-in function that accepts the specified signatures, does appropriate argument
     * and result conversion and calls the provided callable.
     *
     * @param language The Python language object.
     * @param signature The signature ID as defined in {@link GraalHPyDef}.
     * @param name The name of the method.
     * @param callable The native function pointer.
     * @param enclosingType The type the function belongs to (needed for checking of {@code self}).
     * @param factory Just an instance of {@link PythonObjectFactory} to create the function object.
     * @return A {@link PBuiltinFunction} that accepts the given signature.
     */
    @TruffleBoundary
    static PBuiltinFunction createWrapperFunction(PythonLanguage language, HPyFuncSignature signature, String name, Object callable, Object enclosingType, PythonObjectFactory factory) {
        assert InteropLibrary.getUncached(callable).isExecutable(callable) : "object is not callable";
        PRootNode rootNode;
        int numDefaults = 0;
        switch (signature) {
            case NOARGS:
            case UNARYFUNC:
            case REPRFUNC:
            case GETITERFUNC:
            case ITERNEXTFUNC:
            case DESTROYFUNC:
                rootNode = new HPyMethNoargsRoot(language, name, false);
                break;
            case O:
            case BINARYFUNC:
                rootNode = new HPyMethORoot(language, name, false);
                break;
            case KEYWORDS:
                rootNode = new HPyMethKeywordsRoot(language, name);
                break;
            case INITPROC:
                rootNode = new HPyMethInitProcRoot(language, name);
                break;
            case VARARGS:
                rootNode = new HPyMethVarargsRoot(language, name);
                break;
            case TERNARYFUNC:
                rootNode = new HPyMethTernaryRoot(language, name);
                // the third argument is optional
                // so it has a default value (this implicitly is 'None')
                numDefaults = 1;
                break;
            case LENFUNC:
                rootNode = new HPyMethNoargsRoot(language, name, true);
                break;
            case SSIZEOBJARGPROC:
                rootNode = new HPyMethSSizeObjArgProcRoot(language, name);
                break;
            case INQUIRY:
                rootNode = new HPyMethInquiryRoot(language, name);
                break;
            case SSIZEARGFUNC:
                rootNode = new HPyMethSSizeArgFuncRoot(language, name);
                break;
            case OBJOBJPROC:
                rootNode = new HPyMethObjObjProcRoot(language, name);
                break;
            default:
                // TODO(fa): support remaining signatures
                throw CompilerDirectives.shouldNotReachHere("unsupported HPy method signature: " + signature.name());
        }
        Object[] defaults = new Object[numDefaults];
        Arrays.fill(defaults, PNone.NO_VALUE);
        return factory.createBuiltinFunction(name, enclosingType, defaults, new PKeyword[]{new PKeyword(KW_CALLABLE, callable)}, PythonUtils.getOrCreateCallTarget(rootNode));
    }

    /**
     * Creates a built-in function for a specific slot. This built-in function also does appropriate
     * argument and result conversion and calls the provided callable.
     *
     * @param language The Python language object.
     * @param wrapper The wrapper ID as defined in {@link HPySlotWrapper}.
     * @param name The name of the method.
     * @param callable The native function pointer.
     * @param enclosingType The type the function belongs to (needed for checking of {@code self}).
     * @param factory Just an instance of {@link PythonObjectFactory} to create the function object.
     * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot wrapper.
     */
    @TruffleBoundary
    static PBuiltinFunction createWrapperFunction(PythonLanguage language, HPySlotWrapper wrapper, String name, Object callable, Object enclosingType, PythonObjectFactory factory) {
        assert InteropLibrary.getUncached(callable).isExecutable(callable) : "object is not callable";
        PRootNode rootNode;
        int numDefaults = 0;
        switch (wrapper) {
            case NULL:
                rootNode = new HPyMethKeywordsRoot(language, name);
                break;
            case UNARYFUNC:
                rootNode = new HPyMethNoargsRoot(language, name, false);
                break;
            case BINARYFUNC:
            case BINARYFUNC_L:
                rootNode = new HPyMethORoot(language, name, false);
                break;
            case BINARYFUNC_R:
                rootNode = new HPyMethReverseBinaryRoot(language, name, false);
                break;
            case INIT:
                rootNode = new HPyMethInitProcRoot(language, name);
                break;
            case TERNARYFUNC:
                rootNode = new HPyMethTernaryRoot(language, name);
                // the third argument is optional
                // so it has a default value (this implicitly is 'None')
                numDefaults = 1;
                break;
            case LENFUNC:
                rootNode = new HPyMethNoargsRoot(language, name, true);
                break;
            case INQUIRYPRED:
                rootNode = new HPyMethInquiryRoot(language, name);
                break;
            case INDEXARGFUNC:
                rootNode = new HPyMethSSizeArgFuncRoot(language, name);
                break;
            case OBJOBJARGPROC:
                rootNode = new HPyMethObjObjProcRoot(language, name);
                break;
            case SQ_ITEM:
                rootNode = new HPyMethSqItemWrapperRoot(language, name);
                break;
            case SQ_SETITEM:
                rootNode = new HPyMethSqSetitemWrapperRoot(language, name);
                break;
            case SQ_DELITEM:
                // it's really the same as SQ_SETITEM but with a default
                rootNode = new HPyMethSqSetitemWrapperRoot(language, name);
                numDefaults = 1;
                break;
            default:
                // TODO(fa): support remaining slot wrappers
                throw CompilerDirectives.shouldNotReachHere("unsupported HPy slot wrapper: wrap_" + wrapper.name().toLowerCase());
        }
        Object[] defaults = new Object[numDefaults];
        Arrays.fill(defaults, PNone.NO_VALUE);
        return factory.createBuiltinFunction(name, enclosingType, defaults, new PKeyword[]{new PKeyword(KW_CALLABLE, callable)}, PythonUtils.getOrCreateCallTarget(rootNode));
    }

    /**
     * Invokes an HPy C function. It takes care of argument and result conversion and always passes
     * the HPy context as a first parameter.
     */
    abstract static class HPyExternalFunctionInvokeNode extends Node implements IndirectCallNode {

        @Child private HPyConvertArgsToSulongNode toSulongNode;
        @Child private HPyCheckFunctionResultNode checkFunctionResultNode;
        @Child private HPyCloseArgHandlesNode handleCloseNode;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

        HPyExternalFunctionInvokeNode() {
            this.toSulongNode = HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = HPyCheckHandleResultNodeGen.create();
            this.handleCloseNode = this.toSulongNode.createCloseHandleNode();
        }

        HPyExternalFunctionInvokeNode(HPyConvertArgsToSulongNode convertArgsNode) {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = HPyCheckHandleResultNodeGen.create();
            this.handleCloseNode = this.toSulongNode.createCloseHandleNode();
        }

        HPyExternalFunctionInvokeNode(HPyCheckFunctionResultNode checkFunctionResultNode, HPyConvertArgsToSulongNode convertArgsNode) {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = checkFunctionResultNode != null ? checkFunctionResultNode : HPyCheckHandleResultNodeGen.create();
            this.handleCloseNode = this.toSulongNode.createCloseHandleNode();
        }

        public abstract Object execute(VirtualFrame frame, String name, Object callable, Object[] frameArgs);

        @Specialization(limit = "1")
        Object doIt(VirtualFrame frame, String name, Object callable, Object[] arguments,
                        @CachedLibrary("callable") InteropLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached ForeignCallContext foreignCallContext,
                        @Cached PRaiseNode raiseNode) {
            Object[] convertedArguments = new Object[arguments.length + 1];
            GraalHPyContext hPyContext = ctx.getHPyContext();
            toSulongNode.executeInto(frame, hPyContext, arguments, 0, convertedArguments, 1);

            // first arg is always the HPyContext
            convertedArguments[0] = hPyContext;

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = foreignCallContext.enter(frame, ctx, this);

            try {
                return checkFunctionResultNode.execute(ctx, name, lib.execute(callable, convertedArguments));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s failed: %m", name, e);
            } catch (ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s expected %d arguments but got %d.", name, e.getExpectedArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, ctx.getCaughtException());
                foreignCallContext.exit(frame, ctx, state);

                // close all handles (if necessary)
                if (handleCloseNode != null) {
                    handleCloseNode.executeInto(frame, hPyContext, convertedArguments, 1);
                }
            }
        }

        @Override
        public Assumption needNotPassFrameAssumption() {
            return nativeCodeDoesntNeedMyFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return nativeCodeDoesntNeedExceptionState;
        }

        @Override
        public Node copy() {
            HPyExternalFunctionInvokeNode node = (HPyExternalFunctionInvokeNode) super.copy();
            node.nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            node.nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            return node;
        }
    }

    abstract static class HPyMethodDescriptorRootNode extends PRootNode {
        @Child private CalleeContext calleeContext;
        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;

        private final String name;

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, String name, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
            super(language);
            this.name = name;
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(convertArgsToSulongNode);
        }

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, String name, HPyCheckFunctionResultNode checkFunctionResultNode, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
            super(language);
            this.name = name;
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(checkFunctionResultNode, convertArgsToSulongNode);
        }

        protected static Object intToBoolean(Object result) {
            if (result instanceof Integer) {
                return ((Integer) result) != 0;
            } else if (result instanceof Long) {
                return ((Long) result) != 0;
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                return processResult(frame, invokeNode.execute(frame, name, callable, prepareCArguments(frame)));
            } finally {
                getCalleeContext().exit(frame, this);
            }
        }

        protected abstract Object[] prepareCArguments(VirtualFrame frame);

        protected Object processResult(@SuppressWarnings("unused") VirtualFrame frame, Object result) {
            return result;
        }

        protected final Object getSelf(VirtualFrame frame) {
            if (readSelfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readSelfNode = insert(ReadIndexedArgumentNode.create(0));
            }
            return readSelfNode.execute(frame);
        }

        private CalleeContext getCalleeContext() {
            if (calleeContext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                calleeContext = insert(CalleeContext.create());
            }
            return calleeContext;
        }

        private ReadIndexedArgumentNode ensureReadCallableNode() {
            if (readCallableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode;
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public NodeCost getCost() {
            // this is just a thin argument shuffling wrapper
            return NodeCost.NONE;
        }

        @Override
        public String toString() {
            return "<METH root " + name + ">";
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    static final class HPyMethNoargsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        public HPyMethNoargsRoot(PythonLanguage language, String name, boolean nativePrimitiveResult) {
            super(language, name, nativePrimitiveResult ? HPyCheckPrimitiveResultNodeGen.create() : HPyCheckHandleResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame)};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethORoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "arg"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArgNode;

        public HPyMethORoot(PythonLanguage language, String name, boolean nativePrimitiveResult) {
            super(language, name, nativePrimitiveResult ? HPyCheckPrimitiveResultNodeGen.create() : HPyCheckHandleResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), getArg(frame)};
        }

        private Object getArg(VirtualFrame frame) {
            if (readArgNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArgNode = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArgNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethVarargsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;

        @TruffleBoundary
        public HPyMethVarargsRoot(PythonLanguage language, String name) {
            super(language, name, HPyVarargsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(args), (long) args.length};
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(1, true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethKeywordsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @TruffleBoundary
        public HPyMethKeywordsRoot(PythonLanguage language, String name) {
            super(language, name, HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(args), (long) args.length, getKwargs(frame)};
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(1, true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private Object getKwargs(VirtualFrame frame) {
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.createForUserFunction(EMPTY_STRING_ARRAY));
            }
            return readKwargsNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethInitProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @TruffleBoundary
        public HPyMethInitProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(args), (long) args.length, getKwargs(frame)};
        }

        @Override
        @SuppressWarnings("unused")
        protected Object processResult(VirtualFrame frame, Object result) {
            // If no error occurred, the init function always returns None.
            // Possible errors are already handled in the HPyExternalFunctionInvokeNode.
            return PNone.NONE;
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(1, true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private Object getKwargs(VirtualFrame frame) {
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.createForUserFunction(EMPTY_STRING_ARRAY));
            }
            return readKwargsNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethTernaryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"x", "y", "z"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethTernaryRoot(PythonLanguage language, String name) {
            super(language, name, HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            Object arg2 = readArg2Node.execute(frame);
            return arg2 != PNone.NO_VALUE ? arg2 : PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethSSizeArgFuncRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(2, false, -1, false, new String[]{"$self", "n"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;

        public HPyMethSSizeArgFuncRoot(PythonLanguage language, String name) {
            super(language, name, HPySSizeArgFuncToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), getArg1(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethSSizeSSizeArgFuncRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"$self", "n", "m"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethSSizeSSizeArgFuncRoot(PythonLanguage language, String name) {
            super(language, name, HPySSizeArgFuncToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            return readArg2Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Very similar to {@link HPyMethNoargsRoot} but converts the result to a boolean.
     */
    static final class HPyMethInquiryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE);

        public HPyMethInquiryRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame)};
        }

        @Override
        protected Object processResult(VirtualFrame frame, Object result) {
            // 'HPyCheckPrimitiveResultNode' already guarantees that the result is 'int' or 'long'.
            return intToBoolean(result);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethObjObjProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(2, false, -1, false, new String[]{"$self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;

        public HPyMethObjObjProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), getArg1(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        @Override
        protected Object processResult(VirtualFrame frame, Object result) {
            // 'HPyCheckPrimitiveResultNode' already guarantees that the result is 'int' or 'long'.
            return intToBoolean(result);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethSSizeObjArgProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"$self", "arg0", "arg1"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethSSizeObjArgProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPySSizeObjArgProcToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            return readArg2Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethReverseBinaryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readOtherNode;

        public HPyMethReverseBinaryRoot(PythonLanguage language, String name, boolean nativePrimitiveResult) {
            super(language, name, nativePrimitiveResult ? HPyCheckPrimitiveResultNodeGen.create() : HPyCheckHandleResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getOther(frame), getSelf(frame)};
        }

        private Object getOther(VirtualFrame frame) {
            if (readOtherNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readOtherNode = insert(ReadIndexedArgumentNode.create(1));
            }
            return readOtherNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    abstract static class HPyCheckFunctionResultNode extends CheckFunctionResultNode {

        /**
         * Compatiblity method to satisfy the generic interface.
         */
        @Override
        public final Object execute(PythonContext context, String name, Object result) {
            return execute(context, context.getHPyContext(), name, result);
        }

        /**
         * This is the preferred way for executing the node since it avoids unnecessary field reads
         * in the interpreter or multi-context mode.
         */
        public abstract Object execute(PythonContext context, GraalHPyContext nativeContext, String name, Object value);

        protected final void checkFunctionResult(String name, boolean indicatesError, PythonContext context, PRaiseNode raise, PythonObjectFactory factory, PythonLanguage language) {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (indicatesError) {
                // consume exception
                context.setCurrentException(null);
                if (!errOccurred) {
                    throw raise.raise(PythonErrorType.SystemError, ErrorMessages.RETURNED_NULL_WO_SETTING_ERROR, name);
                } else {
                    throw currentException.getExceptionForReraise();
                }
            } else if (errOccurred) {
                // consume exception
                context.setCurrentException(null);
                PBaseException sysExc = factory.createBaseException(PythonErrorType.SystemError, ErrorMessages.RETURNED_RESULT_WITH_ERROR_SET, new Object[]{name});
                sysExc.setCause(currentException.getEscapedException());
                throw PException.fromObject(sysExc, this, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckHandleResultNode extends HPyCheckFunctionResultNode {

        @Specialization(guards = "value == 0")
        Object doIntegerNull(PythonContext context, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, @SuppressWarnings("unused") int value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            // NULL handle must not be closed
            checkFunctionResult(name, true, context, raiseNode, factory, language);
            throw CompilerDirectives.shouldNotReachHere("an exception should have been thrown");
        }

        @Specialization(replaces = "doIntegerNull")
        Object doInteger(PythonContext context, GraalHPyContext nativeContext, String name, int value,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            boolean isNullHandle = value == 0;
            if (!isNullHandle) {
                // Python land is receiving a handle from an HPy extension, so we are now owning the
                // handle and we don't need it any longer. So, close it in every case.
                nativeContext.releaseHPyHandleForObject(value);
            }
            checkFunctionResult(name, isNullHandle, context, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, value);
        }

        @Specialization(guards = "value == 0")
        Object doLongNull(PythonContext context, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, @SuppressWarnings("unused") long value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            // NULL handle must not be closed
            checkFunctionResult(name, true, context, raiseNode, factory, language);
            throw CompilerDirectives.shouldNotReachHere("an exception should have been thrown");
        }

        @Specialization(replaces = "doLongNull")
        Object doLong(PythonContext context, GraalHPyContext nativeContext, String name, long value,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            boolean isNullHandle = value == 0;
            if (!isNullHandle) {
                // Python land is receiving a handle from an HPy extension, so we are now owning the
                // handle and we don't need it any longer. So, close it in every case.
                nativeContext.releaseHPyHandleForObject(value);
            }
            checkFunctionResult(name, isNullHandle, context, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, value);
        }

        @Specialization(guards = "isNullHandle(nativeContext, handle)")
        Object doNullHandle(PythonContext context, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, @SuppressWarnings("unused") GraalHPyHandle handle,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            // NULL handle must not be closed
            checkFunctionResult(name, true, context, raiseNode, factory, language);
            throw CompilerDirectives.shouldNotReachHere("an exception should have been thrown");
        }

        @Specialization(guards = "!isNullHandle(nativeContext, handle)", replaces = "doNullHandle")
        Object doNonNullHandle(PythonContext context, GraalHPyContext nativeContext, String name, GraalHPyHandle handle,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            // Python land is receiving a handle from an HPy extension, so we are now owning the
            // handle and we don't need it any longer. So, close it in every case.
            handle.close(nativeContext, isAllocatedProfile);
            checkFunctionResult(name, false, context, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, handle);
        }

        @Specialization(replaces = {"doIntegerNull", "doNonNullHandle"})
        Object doHandle(PythonContext context, GraalHPyContext nativeContext, String name, GraalHPyHandle handle,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            boolean isNullHandle = isNullHandle(nativeContext, handle);
            if (!isNullHandle) {
                // Python land is receiving a handle from an HPy extension, so we are now owning the
                // handle and we don't need it any longer. So, close it in every case.
                handle.close(nativeContext, isAllocatedProfile);
            }
            checkFunctionResult(name, isNullHandle, context, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, handle);
        }

        @Specialization(replaces = {"doIntegerNull", "doInteger", "doLongNull", "doLong", "doNullHandle", "doNonNullHandle", "doHandle"})
        Object doGeneric(PythonContext context, GraalHPyContext nativeContext, String name, Object value,
                        @Cached HPyEnsureHandleNode ensureHandleNode,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            GraalHPyHandle handle = ensureHandleNode.execute(nativeContext, value);
            boolean isNullHandle = isNullHandle(nativeContext, handle);
            if (!isNullHandle) {
                // Python land is receiving a handle from an HPy extension, so we are now owning the
                // handle and we don't need it any longer. So, close it in every case.
                handle.close(nativeContext, isAllocatedProfile);
            }
            checkFunctionResult(name, isNullHandle(nativeContext, handle), context, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, handle);
        }

        protected static boolean isNullHandle(GraalHPyContext nativeContext, GraalHPyHandle handle) {
            return handle == nativeContext.getNullHandle();
        }
    }

    /**
     * Similar to {@link HPyCheckFunctionResultNode}, this node checks a primitive result of a
     * native function. This node guarantees that an {@code int} or {@code long} is returned.
     */
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckPrimitiveResultNode extends HPyCheckFunctionResultNode {
        public abstract int executeInt(PythonContext context, GraalHPyContext nativeContext, String name, int value);

        public abstract long executeLong(PythonContext context, GraalHPyContext nativeContext, String name, long value);

        @Specialization
        int doInteger(PythonContext context, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, int value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == -1, context, raiseNode, factory, language);
            return value;
        }

        @Specialization(replaces = "doInteger")
        long doLong(PythonContext context, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, long value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == -1, context, raiseNode, factory, language);
            return value;
        }

        @Specialization(limit = "1")
        Object doObject(PythonContext context, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, Object value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @CachedLibrary("value") InteropLibrary lib,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (lib.fitsInLong(value)) {
                try {
                    long lvalue = lib.asLong(value);
                    checkFunctionResult(name, lvalue == -1, context, raiseNode, factory, language);
                    return lvalue;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.raise(SystemError, "function '%s' did not return an integer.", name);
        }
    }
}
