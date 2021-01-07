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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_GET_OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_POLYGLOT_FROM_TYPED;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_PTR_ADD;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_PTR_COMPARE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_PY_FLOAT_AS_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbols.FUN_WHCAR_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_REFCNT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.GetAttrNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AllToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AllToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.BinaryFirstToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CextUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CharPtrToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.DirectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FastCallArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FastCallWithKeywordsArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetNativeNullNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetTypeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.IsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ObjectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TernaryFirstSecondToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TernaryFirstThirdToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.VoidPtrToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache.ResolveNativeReferenceNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PGetDynamicTypeNode.GetSulongTypeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ImportCExtSymbolNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.GetVaArgsNode;
import com.oracle.graal.python.builtins.objects.cext.common.GetVaArgsNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class CExtNodes {

    @GenerateUncached
    abstract static class ImportCAPISymbolNode extends PNodeWithContext {

        public abstract Object execute(String name);

        @Specialization
        static Object doGeneric(String name,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode) {
            return importCExtSymbolNode.execute(context.getCApiContext(), name);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    @ImportStatic({PGuards.class})
    public abstract static class SubtypeNew extends Node {
        /**
         * typenamePrefix the <code>typename</code> in <code>typename_subtype_new</code>
         */
        protected String getTypenamePrefix() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
        }

        protected abstract Object execute(Object object, Object arg);

        protected String getFunctionName() {
            return getTypenamePrefix() + "_subtype_new";
        }

        @Specialization(guards = "isNativeClass(object)")
        protected Object callNativeConstructor(Object object, Object arg,
                        @Exclusive @Cached("getFunctionName()") String functionName,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                Object result = interopLibrary.execute(importCAPISymbolNode.execute(functionName), toSulongNode.execute(object), arg);
                return toJavaNode.execute(result);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("C subtype_new function failed", e);
            }
        }
    }

    public abstract static class FloatSubtypeNew extends SubtypeNew {
        @Override
        protected final String getTypenamePrefix() {
            return "float";
        }

        public final Object call(Object object, double arg) {
            return execute(object, arg);
        }

        public static FloatSubtypeNew create() {
            return CExtNodesFactory.FloatSubtypeNewNodeGen.create();
        }
    }

    public abstract static class TupleSubtypeNew extends SubtypeNew {

        @Child private ToSulongNode toSulongNode;

        @Override
        protected final String getTypenamePrefix() {
            return "tuple";
        }

        public final Object call(Object object, Object arg) {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return execute(object, toSulongNode.execute(arg));
        }

        public static TupleSubtypeNew create() {
            return CExtNodesFactory.TupleSubtypeNewNodeGen.create();
        }
    }

    public abstract static class StringSubtypeNew extends SubtypeNew {
        @Child private ToSulongNode toSulongNode;

        @Override
        protected final String getTypenamePrefix() {
            return "unicode";
        }

        public final Object call(Object object, Object arg) {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return execute(object, toSulongNode.execute(arg));
        }

        public static StringSubtypeNew create() {
            return CExtNodesFactory.StringSubtypeNewNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class FromNativeSubclassNode extends Node {

        public abstract Double execute(VirtualFrame frame, PythonNativeObject object);

        @Specialization
        @SuppressWarnings("unchecked")
        public Double execute(VirtualFrame frame, PythonNativeObject object,
                        @Exclusive @Cached GetClassNode getClass,
                        @Exclusive @Cached IsSubtypeNode isSubtype,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            if (isFloatSubtype(frame, object, getClass, isSubtype, context)) {
                try {
                    return (Double) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PY_FLOAT_AS_DOUBLE), toSulongNode.execute(object));
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("C object conversion function failed", e);
                }
            }
            return null;
        }

        public boolean isFloatSubtype(VirtualFrame frame, PythonNativeObject object, GetClassNode getClass, IsSubtypeNode isSubtype, PythonContext context) {
            return isSubtype.execute(frame, getClass.execute(object), context.getCore().lookupType(PythonBuiltinClassType.PFloat));
        }

        public static FromNativeSubclassNode create() {
            return CExtNodesFactory.FromNativeSubclassNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    @ReportPolymorphism
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ToSulongNode extends CExtToNativeNode {

        @Specialization
        static Object doString(@SuppressWarnings("unused") CExtContext cextContext, String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return PythonObjectNativeWrapper.wrap(factory.createString(str), noWrapperProfile);
        }

        @Specialization
        static Object doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PythonCore core = contextRef.get().getCore();
            PInt boxed = b ? core.getTrue() : core.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization(guards = "isSmallInteger(i)")
        static PrimitiveNativeWrapper doIntegerSmall(@SuppressWarnings("unused") CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                return context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "!isSmallInteger(i)")
        static PrimitiveNativeWrapper doInteger(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "isSmallLong(l)")
        static PrimitiveNativeWrapper doLongSmall(@SuppressWarnings("unused") CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                return context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isSmallLong(l)")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, double d) {
            return PrimitiveNativeWrapper.createDouble(d);
        }

        @Specialization(guards = "isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") double d,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            PFloat boxed = contextRef.get().getCore().getNaN();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            // Use a counting profile since we should enter the branch just once per context.
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
                // code will happen just once per context.
                CompilerDirectives.transferToInterpreter();
                nativeWrapper = PrimitiveNativeWrapper.createDouble(Double.NaN);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractNativeObject nativeObject) {
            return nativeObject.getPtr();
        }

        @Specialization
        static Object doNativeNull(@SuppressWarnings("unused") CExtContext cextContext, PythonNativeNull object) {
            return object.getPtr();
        }

        @Specialization
        static Object doDeleteMarker(@SuppressWarnings("unused") CExtContext cextContext, DescriptorDeleteMarker marker,
                        @Cached GetNativeNullNode getNativeNullNode) {
            assert marker == DescriptorDeleteMarker.INSTANCE;
            PythonNativeNull nativeNull = (PythonNativeNull) getNativeNullNode.execute();
            return nativeNull.getPtr();
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        static Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return doSingleton(cextContext, cachedObject, contextRef);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        static Object doSingleton(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PythonNativeWrapper nativeWrapper = context.getSingletonNativeWrapper(object);
            if (nativeWrapper == null) {
                // this will happen just once per context and special singleton
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeWrapper = new PythonObjectNativeWrapper(object);
                // this should keep the native wrapper alive forever
                nativeWrapper.increaseRefCount();
                context.setSingletonNativeWrapper(object, nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization(guards = "object == cachedObject", limit = "3", assumptions = "singleContextAssumption()")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonManagedClass cachedObject,
                        @Cached(value = "wrapNativeClass(object)", weak = true) PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrap(object, getNameNode.execute(object));
        }

        @Specialization(guards = "object == cachedObject", limit = "3", assumptions = "singleContextAssumption()")
        static Object doPythonType(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonBuiltinClassType object,
                        @SuppressWarnings("unused") @Cached("object") PythonBuiltinClassType cachedObject,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached("wrapNativeClass(ctx, object)") PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonType")
        static Object doPythonTypeUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonBuiltinClassType object,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrap(ctx.getCore().lookupType(object), getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
        }

        @Specialization(guards = {"lib.isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        static Object doForeignObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return TruffleObjectNativeWrapper.wrap(object);
        }

        @Specialization(guards = "isFallback(object, lib)")
        static Object run(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            assert object != null : "Java 'null' cannot be a Sulong value";
            Object o = lib.getDelegatedValue(object);
            assert CApiGuards.isNativeWrapper(o) : "unknown object cannot be a Sulong value";
            return o;
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonContext ctx, PythonBuiltinClassType object) {
            return PythonClassNativeWrapper.wrap(ctx.getCore().lookupType(object), GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, PythonObjectLibrary lib) {
            return !(object instanceof String || object instanceof Boolean || object instanceof Integer || object instanceof Long || object instanceof Double ||
                            object instanceof PythonBuiltinClassType || object instanceof PythonNativeNull || object == DescriptorDeleteMarker.INSTANCE ||
                            object instanceof PythonAbstractObject) && !(lib.isForeignObject(object) && !CApiGuards.isNativeWrapper(object));
        }

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }

        public static ToSulongNode create() {
            return CExtNodesFactory.ToSulongNodeGen.create();
        }

        public static ToSulongNode getUncached() {
            return CExtNodesFactory.ToSulongNodeGen.getUncached();
        }
    }

    /**
     * Same as {@code ToSulongNode} but ensures that a new Python reference is returned.<br/>
     * Concept:<br/>
     * <p>
     * If the value to convert is a managed object or a Java primitive, we will (1) do nothing if a
     * fresh wrapper is created, or (2) increase the reference count by 1 if the wrapper already
     * exists.
     * </p>
     * <p>
     * If the value to convert is a {@link PythonAbstractNativeObject} (i.e. a wrapped native
     * pointer), the reference count will be increased by 1. This is necessary because if the
     * currently returning upcall function already got a new reference, it won't have increased the
     * refcnt but will eventually decreases it.<br/>
     * Consider following example:<br/>
     *
     * <pre>
     *     some.py: nativeLong0 * nativeLong1
     * </pre>
     *
     * Assume that {@code nativeLong0} is a native object with a native type. It will call
     * {@code nativeType->tp_as_number.nb_multiply}. This one then often uses
     * {@code PyNumber_Multiply} which should just pass through the newly created native reference.
     * But it will decrease the reference count since it wraps the gained native pointer. So, the
     * intermediate upcall should effectively not alter the refcnt which means that we need to
     * increase it since it will finally decrease it.
     * </p>
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    @ReportPolymorphism
    public abstract static class ToNewRefNode extends CExtToNativeNode {

        public final Object executeInt(int i) {
            return executeInt(CExtContext.LAZY_CONTEXT, i);
        }

        public final Object executeLong(long l) {
            return executeLong(CExtContext.LAZY_CONTEXT, l);
        }

        public abstract Object executeInt(CExtContext cExtContext, int i);

        public abstract Object executeLong(CExtContext cExtContext, long l);

        @Specialization
        static Object doString(CExtContext cextContext, String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return ToSulongNode.doString(cextContext, str, factory, noWrapperProfile);
        }

        @Specialization
        static Object doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PythonCore core = contextRef.get().getCore();
            PInt boxed = b ? core.getTrue() : core.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization(guards = "isSmallInteger(i)")
        static PrimitiveNativeWrapper doIntegerSmall(@SuppressWarnings("unused") CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                PrimitiveNativeWrapper cachedPrimitiveNativeWrapper = context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
                cachedPrimitiveNativeWrapper.increaseRefCount();
                return cachedPrimitiveNativeWrapper;
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "!isSmallInteger(i)")
        static PrimitiveNativeWrapper doInteger(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "isSmallLong(l)")
        static PrimitiveNativeWrapper doLongSmall(@SuppressWarnings("unused") CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                PrimitiveNativeWrapper cachedPrimitiveNativeWrapper = context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
                cachedPrimitiveNativeWrapper.increaseRefCount();
                return cachedPrimitiveNativeWrapper;
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isSmallLong(l)")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d) {
            return ToSulongNode.doDouble(cextContext, d);
        }

        @Specialization(guards = "isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") double d,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            PFloat boxed = contextRef.get().getCore().getNaN();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            // Use a counting profile since we should enter the branch just once per context.
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
                // code will happen just once per context.
                CompilerDirectives.transferToInterpreter();
                nativeWrapper = PrimitiveNativeWrapper.createDouble(Double.NaN);
                boxed.setNativeWrapper(nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization
        static Object doNativeObject(CExtContext cextContext, PythonAbstractNativeObject nativeObject,
                        @Cached AddRefCntNode refCntNode) {
            Object res = ToSulongNode.doNativeObject(cextContext, nativeObject);
            refCntNode.inc(res);
            return res;
        }

        @Specialization
        static Object doNativeNull(CExtContext cextContext, PythonNativeNull object) {
            return ToSulongNode.doNativeNull(cextContext, object);
        }

        @Specialization
        static Object doDeleteMarker(CExtContext cextContext, DescriptorDeleteMarker marker,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return ToSulongNode.doDeleteMarker(cextContext, marker, getNativeNullNode);
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        static Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return doSingleton(cextContext, cachedObject, contextRef);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        static Object doSingleton(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PythonNativeWrapper nativeWrapper = context.getSingletonNativeWrapper(object);
            if (nativeWrapper == null) {
                // this will happen just once per context and special singleton
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeWrapper = new PythonObjectNativeWrapper(object);
                // this should keep the native wrapper alive forever
                nativeWrapper.increaseRefCount();
                context.setSingletonNativeWrapper(object, nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization(guards = "object == cachedObject", limit = "3", assumptions = "singleContextAssumption()")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonManagedClass cachedObject,
                        @Cached(value = "wrapNativeClass(object)", weak = true) PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(object, getNameNode.execute(object));
        }

        @Specialization(guards = "object == cachedObject", limit = "3", assumptions = "singleContextAssumption()")
        static Object doPythonType(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonBuiltinClassType object,
                        @SuppressWarnings("unused") @Cached("object") PythonBuiltinClassType cachedObject,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached("wrapNativeClass(ctx, object)") PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonType")
        static Object doPythonTypeUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonBuiltinClassType object,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(ctx.getCore().lookupType(object), getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(object, noWrapperProfile);
        }

        @Specialization(guards = {"lib.isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        static Object doForeignObject(CExtContext cextContext, TruffleObject object,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            // this will always be a new wrapper; it's implicitly always a new reference in any case
            return ToSulongNode.doForeignObject(cextContext, object, lib);
        }

        @Specialization(guards = "isFallback(object, lib)")
        static Object run(CExtContext cextContext, Object object,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return ToSulongNode.run(cextContext, object, lib);
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonContext ctx, PythonBuiltinClassType object) {
            return PythonClassNativeWrapper.wrap(ctx.getCore().lookupType(object), GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, PythonObjectLibrary lib) {
            return ToSulongNode.isFallback(object, lib);
        }

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }
    }

    /**
     * Same as {@link ToNewRefNode} but does not create new references for
     * {@link PythonAbstractNativeObject}.<br/>
     * This node should only be used to convert arguments for a native call. It will increase the
     * ref count of all {@link PythonNativeWrapper} (and subclasses) (but not if they are newly
     * created since the ref count is already one in this case). But it does not increase the ref
     * count on {@link PythonAbstractNativeObject}.
     *
     * The reason for this behavior is that after the native function returns, one can decrease the
     * ref count by one and therefore release any allocated handles that would cause a memory leak.
     * This is not necessary for {@link PythonAbstractNativeObject} since they are managed by a weak
     * reference and thus we save certainly expensive access to the native {@code ob_refcnt} member.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ToBorrowedRefNode extends CExtToNativeNode {

        public final Object executeInt(int i) {
            return executeInt(CExtContext.LAZY_CONTEXT, i);
        }

        public final Object executeLong(long l) {
            return executeLong(CExtContext.LAZY_CONTEXT, l);
        }

        public abstract Object executeInt(CExtContext cExtContext, int i);

        public abstract Object executeLong(CExtContext cExtContext, long l);

        @Specialization
        static Object doString(CExtContext cextContext, String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return ToSulongNode.doString(cextContext, str, factory, noWrapperProfile);
        }

        @Specialization
        static Object doBoolean(CExtContext cextContext, boolean b,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            return ToNewRefNode.doBoolean(cextContext, b, contextRef, profile);
        }

        @Specialization(guards = "isSmallInteger(i)")
        static PrimitiveNativeWrapper doIntegerSmall(CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doIntegerSmall(cextContext, i, contextRef);
        }

        @Specialization(guards = "!isSmallInteger(i)")
        static PrimitiveNativeWrapper doInteger(CExtContext cextContext, int i) {
            return ToNewRefNode.doInteger(cextContext, i);
        }

        @Specialization(guards = "isSmallLong(l)")
        static PrimitiveNativeWrapper doLongSmall(CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doLongSmall(cextContext, l, contextRef);
        }

        @Specialization(guards = "!isSmallLong(l)")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return ToNewRefNode.doLong(cextContext, l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d) {
            return ToSulongNode.doDouble(cextContext, d);
        }

        @Specialization(guards = "isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            return ToNewRefNode.doDouble(cextContext, d, contextRef, noWrapperProfile);
        }

        @Specialization
        static Object doNativeObject(CExtContext cextContext, PythonAbstractNativeObject nativeObject) {
            return ToSulongNode.doNativeObject(cextContext, nativeObject);
        }

        @Specialization
        static Object doNativeNull(CExtContext cextContext, PythonNativeNull object) {
            return ToSulongNode.doNativeNull(cextContext, object);
        }

        @Specialization
        static Object doDeleteMarker(CExtContext cextContext, DescriptorDeleteMarker marker,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return ToSulongNode.doDeleteMarker(cextContext, marker, getNativeNullNode);
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        static Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return doSingleton(cextContext, cachedObject, contextRef);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        static Object doSingleton(CExtContext cextContext, PythonAbstractObject object,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doSingleton(cextContext, object, contextRef);
        }

        @Specialization(guards = "object == cachedObject", limit = "3", assumptions = "singleContextAssumption()")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonManagedClass cachedObject,
                        @Cached(value = "wrapNativeClass(object)", weak = true) PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(object, getNameNode.execute(object));
        }

        @Specialization(guards = "object == cachedObject", limit = "3", assumptions = "singleContextAssumption()")
        static Object doPythonType(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonBuiltinClassType object,
                        @SuppressWarnings("unused") @Cached("object") PythonBuiltinClassType cachedObject,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached("wrapNativeClass(ctx, object)") PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonType")
        static Object doPythonTypeUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonBuiltinClassType object,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(ctx.getCore().lookupType(object), getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(object, noWrapperProfile);
        }

        @Specialization(guards = {"lib.isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        static Object doForeignObject(CExtContext cextContext, TruffleObject object,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            // this will always be a new wrapper; it's implicitly always a new reference in any case
            return ToSulongNode.doForeignObject(cextContext, object, lib);
        }

        @Specialization(guards = "isFallback(object, lib)")
        static Object run(CExtContext cextContext, Object object,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return ToSulongNode.run(cextContext, object, lib);
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonContext ctx, PythonBuiltinClassType object) {
            return PythonClassNativeWrapper.wrap(ctx.getCore().lookupType(object), GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, PythonObjectLibrary lib) {
            return ToSulongNode.isFallback(object, lib);
        }

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Unwraps objects contained in {@link PythonObjectNativeWrapper} instances or wraps objects
     * allocated in native code for consumption in Java.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectBaseNode extends CExtAsPythonObjectNode {

        @Specialization(guards = "object.isBool()")
        static boolean doBoolNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object) {
            return object.getBool();
        }

        @Specialization(guards = {"object.isByte()", "!isNative(isPointerNode, object)"}, limit = "1")
        static byte doByteNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getByte();
        }

        @Specialization(guards = {"object.isInt()", "mayUsePrimitive(isPointerNode, object)"}, limit = "1")
        static int doIntNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getInt();
        }

        @Specialization(guards = {"object.isInt() || object.isLong()", "mayUsePrimitive(isPointerNode, object)"}, //
                        limit = "1", //
                        replaces = "doIntNativeWrapper")
        static long doLongNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getLong();
        }

        @Specialization(guards = {"object.isDouble()", "!isNative(isPointerNode, object)"}, limit = "1")
        static double doDoubleNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getDouble();
        }

        @Specialization(guards = {"!object.isBool()", "isNative(isPointerNode, object)", "!mayUsePrimitive(isPointerNode, object)"}, limit = "1")
        static Object doPrimitiveNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Exclusive @Cached MaterializeDelegateNode materializeNode,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return materializeNode.execute(object);
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", limit = "1")
        static Object doNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            return lib.getDelegate(object);
        }

        @Specialization
        static PythonNativeNull doNativeNull(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonNativeNull object) {
            return object;
        }

        @Specialization
        static PythonAbstractObject doPythonObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object) {
            return object;
        }

        @Specialization
        static String doString(@SuppressWarnings("unused") CExtContext cextContext, String object) {
            return object;
        }

        @Specialization
        static boolean doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b) {
            return b;
        }

        @Specialization
        static byte doLong(@SuppressWarnings("unused") CExtContext cextContext, byte b) {
            return b;
        }

        @Specialization
        static int doLong(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return i;
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return l;
        }

        @Specialization
        static double doDouble(@SuppressWarnings("unused") CExtContext cextContext, double d) {
            return d;
        }

        @Specialization(guards = "isFallback(obj, lib, isForeignClassProfile)", limit = "3")
        static Object run(@SuppressWarnings("unused") CExtContext cextContext, Object obj,
                        @SuppressWarnings("unused") @CachedLibrary("obj") PythonObjectLibrary lib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.INVALID_OBJ_FROM_NATIVE, obj);
        }

        protected static boolean isFallback(Object obj, PythonObjectLibrary lib, IsBuiltinClassProfile isForeignClassProfile) {
            if (CApiGuards.isNativeWrapper(obj)) {
                return false;
            }
            if (CApiGuards.isNativeNull(obj)) {
                return false;
            }
            if (obj == DescriptorDeleteMarker.INSTANCE) {
                return false;
            }
            if (PGuards.isAnyPythonObject(obj)) {
                return false;
            }
            if (isForeignObject(obj, lib, isForeignClassProfile)) {
                return false;
            }
            if (PGuards.isString(obj)) {
                return false;
            }
            return !(obj instanceof Boolean || obj instanceof Byte || obj instanceof Integer || obj instanceof Long || obj instanceof Double);
        }

        static boolean mayUsePrimitive(IsPointerNode isPointerNode, PrimitiveNativeWrapper object) {
            // For wrappers around small integers, it does not matter if they received "to-native"
            // because pointer equality is still ensured since they are globally cached in the
            // context.
            return (object.isInt() || object.isLong()) && (CApiGuards.isSmallLong(object.getLong()) || !isPointerNode.execute(object));
        }

        protected static boolean isNative(IsPointerNode isPointerNode, PythonNativeWrapper object) {
            return isPointerNode.execute(object);
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
        }

        protected static boolean isForeignObject(Object obj, PythonObjectLibrary lib, IsBuiltinClassProfile isForeignClassProfile) {
            return isForeignClassProfile.profileClass(lib.getLazyPythonClass(obj), PythonBuiltinClassType.ForeignObject);
        }
    }

    /**
     * Unwraps objects contained in {@link DynamicObjectNativeWrapper.PythonObjectNativeWrapper}
     * instances or wraps objects allocated in native code for consumption in Java.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "2")
        static PythonAbstractObject doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createBinaryProfile()") ConditionProfile newRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile validRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile resurrectProfile,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GetRefCntNode getRefCntNode,
                        @Cached AddRefCntNode addRefCntNode,
                        @Cached AttachLLVMTypeNode attachLLVMTypeNode) {
            if (lib.isNull(object)) {
                return PNone.NO_VALUE;
            }
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                return cApiContext.getPythonNativeObject(object, newRefProfile, validRefProfile, resurrectProfile, getRefCntNode, addRefCntNode, attachLLVMTypeNode);
            }
            return new PythonAbstractNativeObject(object);
        }

    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectStealingNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static PythonAbstractObject doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @Cached("createBinaryProfile()") ConditionProfile newRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile validRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile resurrectProfile,
                        @CachedLibrary("object") InteropLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GetRefCntNode getRefCntNode,
                        @Cached AddRefCntNode addRefCntNode,
                        @Cached AttachLLVMTypeNode attachLLVMTypeNode) {
            if (lib.isNull(object)) {
                return PNone.NO_VALUE;
            }
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                return cApiContext.getPythonNativeObject(object, newRefProfile, validRefProfile, resurrectProfile, getRefCntNode, addRefCntNode, true, attachLLVMTypeNode);
            }
            return new PythonAbstractNativeObject(object);
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class WrapVoidPtrNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile) {
            // TODO(fa): should we use a different wrapper for non-'PyObject*' pointers; they cannot
            // be used in the user value space but might be passed-through

            // do not modify reference count at all; this is for non-'PyObject*' pointers
            return new PythonAbstractNativeObject(object);
        }

    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class WrapCharPtrNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @Cached FromCharPointerNode fromCharPointerNode) {
            return fromCharPointerNode.execute(object);
        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Materializes a primitive value of a primitive native wrapper to ensure pointer equality.
     */
    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class MaterializeDelegateNode extends Node {

        public abstract Object execute(PythonNativeWrapper object);

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isBool()"}, limit = "1")
        static PInt doBoolNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            // Special case for True and False: use singletons
            PythonCore core = context.getCore();
            PInt materializedInt = object.getBool() ? core.getTrue() : core.getFalse();
            object.setMaterializedObject(materializedInt);

            // If the singleton already has a native wrapper, we may need to update the pointer
            // of wrapper 'object' since the native could code see the same pointer.
            if (materializedInt.getNativeWrapper() != null) {
                object.setNativePointer(lib.getNativePointer(materializedInt.getNativeWrapper()));
            } else {
                materializedInt.setNativeWrapper(object);
            }
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isByte()"}, limit = "1")
        static PInt doByteNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getByte());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isInt()"}, limit = "1")
        static PInt doIntNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getInt());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isLong()"}, limit = "1")
        static PInt doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getLong());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isDouble()", "!isNaN(object)"}, limit = "1")
        static PFloat doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PFloat materializedInt = factory.createFloat(object.getDouble());
            materializedInt.setNativeWrapper(object);
            object.setMaterializedObject(materializedInt);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isDouble()", "isNaN(object)"}, limit = "1")
        static PFloat doDoubleNativeWrapperNaN(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            // Special case for double NaN: use singleton
            PFloat materializedFloat = context.getCore().getNaN();
            object.setMaterializedObject(materializedFloat);

            // If the NaN singleton already has a native wrapper, we may need to update the
            // pointer
            // of wrapper 'object' since the native code should see the same pointer.
            if (materializedFloat.getNativeWrapper() != null) {
                object.setNativePointer(lib.getNativePointer(materializedFloat.getNativeWrapper()));
            } else {
                materializedFloat.setNativeWrapper(object);
            }
            return materializedFloat;
        }

        @Specialization(guards = {"object.getClass() == cachedClass", "isMaterialized(object, lib)"}, limit = "1")
        static Object doMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends DynamicObjectNativeWrapper.PrimitiveNativeWrapper> cachedClass) {
            return lib.getDelegate(CompilerDirectives.castExact(object, cachedClass));
        }

        @Specialization(guards = {"!isPrimitiveNativeWrapper(object)", "object.getClass() == cachedClass"}, limit = "3")
        static Object doNativeWrapper(PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends PythonNativeWrapper> cachedClass) {
            return lib.getDelegate(CompilerDirectives.castExact(object, cachedClass));
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", replaces = "doNativeWrapper", limit = "1")
        static Object doNativeWrapperGeneric(PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            return lib.getDelegate(object);
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
        }

        protected static boolean isNaN(PrimitiveNativeWrapper object) {
            assert object.isDouble();
            return Double.isNaN(object.getDouble());
        }

        static boolean isMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper wrapper, PythonNativeWrapperLibrary lib) {
            return wrapper.getMaterializedObject(lib) != null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * use subclasses {@link ToJavaNode} and {@link ToJavaStealingNode}
     */
    abstract static class ToJavaBaseNode extends CExtToJavaNode {

        @Specialization
        static Object doWrapper(@SuppressWarnings("unused") CExtContext nativeContext, PythonNativeWrapper value,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(value);
        }

        @Specialization
        static PythonAbstractObject doPythonObject(@SuppressWarnings("unused") CExtContext nativeContext, PythonAbstractObject value) {
            return value;
        }

        @Specialization
        static String doString(@SuppressWarnings("unused") CExtContext nativeContext, String object) {
            return object;
        }

        @Specialization
        static boolean doBoolean(@SuppressWarnings("unused") CExtContext nativeContext, boolean b) {
            return b;
        }

        @Specialization
        static int doInt(@SuppressWarnings("unused") CExtContext nativeContext, int i) {
            // Note: Sulong guarantees that an integer won't be a pointer
            return i;
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") CExtContext nativeContext, long l) {
            return l;
        }

        @Specialization
        static byte doByte(@SuppressWarnings("unused") CExtContext nativeContext, byte b) {
            return b;
        }

        @Specialization
        static double doDouble(@SuppressWarnings("unused") CExtContext nativeContext, double d) {
            return d;
        }

        protected static boolean isForeignObject(Object obj) {
            return !(obj instanceof PythonAbstractObject || obj instanceof PythonNativeWrapper || obj instanceof String || obj instanceof Boolean || obj instanceof Integer ||
                            obj instanceof Long || obj instanceof Byte || obj instanceof Double);
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class ToJavaNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("resolveNativeReferenceNode") @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                        @Shared("toJavaNode") @Cached AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            // this is just a shortcut
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            return asPythonObjectNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(value), false));
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class ToJavaStealingNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("resolveNativeReferenceNode") @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                        @Shared("toJavaStealingNode") @Cached AsPythonObjectStealingNode toJavaNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            return toJavaNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(value), true));
        }
    }

    /**
     * Does the same conversion as the native function {@code native_pointer_to_java}. The node
     * tries to avoid calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class VoidPtrToJavaNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("toJavaNode") @Cached WrapVoidPtrNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return new PythonAbstractNativeObject((TruffleObject) value);
            }
            return asPythonObjectNode.execute(resolveHandleNode.execute(value));
        }
    }

    /**
     * Does the same conversion as the native function {@code native_pointer_to_java}. The node
     * tries to avoid calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class CharPtrToJavaNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("toJavaNode") @Cached WrapCharPtrNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return asPythonObjectNode.execute(value);
            }
            return asPythonObjectNode.execute(resolveHandleNode.execute(value));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class AsCharPointerNode extends Node {
        public abstract Object execute(Object obj);

        @Specialization
        Object doPString(PString str,
                        @Shared("callStringToCstrNode") @Cached PCallCapiFunction callStringToCstrNode) {
            String value = str.getValue();
            return callStringToCstrNode.call(FUN_PY_TRUFFLE_STRING_TO_CSTR, value, value.length());
        }

        @Specialization
        Object doString(String str,
                        @Shared("callStringToCstrNode") @Cached PCallCapiFunction callStringToCstrNode) {
            return callStringToCstrNode.call(FUN_PY_TRUFFLE_STRING_TO_CSTR, str, str.length());
        }

        @Specialization
        Object doByteArray(byte[] arr,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Exclusive @Cached PCallCapiFunction callByteArrayToNativeNode) {
            return callByteArrayToNativeNode.call(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE, context.getEnv().asGuestValue(arr), arr.length);
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static AsCharPointerNode create() {
            return CExtNodesFactory.AsCharPointerNodeGen.create();
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static AsCharPointerNode getUncached() {
            return CExtNodesFactory.AsCharPointerNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class FromCharPointerNode extends Node {
        public abstract Object execute(Object charPtr);

        @Specialization(limit = "1")
        static String doCStringWrapper(CStringWrapper cStringWrapper,
                        @CachedLibrary("cStringWrapper") PythonNativeWrapperLibrary lib) {
            return cStringWrapper.getString(lib);
        }

        @Specialization(limit = "1")
        static String doCByteArrayWrapper(CByteArrayWrapper cByteArrayWrapper,
                        @CachedLibrary("cByteArrayWrapper") PythonNativeWrapperLibrary lib) {
            byte[] byteArray = cByteArrayWrapper.getByteArray(lib);
            // TODO(fa): what is the encoding ? ASCII only ?
            return PythonUtils.newString(byteArray);
        }

        @Specialization(guards = "!isCArrayWrapper(charPtr)")
        PString doPointer(Object charPtr,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(new NativeCharSequence(charPtr, 1, false));
        }

        static boolean isCArrayWrapper(Object object) {
            return object instanceof CArrayWrapper;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class SizeofWCharNode extends Node {

        public abstract long execute();

        @Specialization
        long doCached(
                        @Exclusive @Cached(value = "getWcharSize()", allowUncached = true) long wcharSize) {
            return wcharSize;
        }

        protected static long getWcharSize() {
            long wcharSize = (long) PCallCapiFunction.getUncached().call(FUN_WHCAR_SIZE);
            assert wcharSize >= 0L;
            return wcharSize;
        }

        public static SizeofWCharNode create() {
            return CExtNodesFactory.SizeofWCharNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PointerCompareNode extends Node {
        public abstract boolean execute(String opName, Object a, Object b);

        private static boolean executeCFunction(int op, Object a, Object b, InteropLibrary interopLibrary, ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                return (int) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PTR_COMPARE), a, b, op) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(FUN_PTR_COMPARE + " didn't work!");
            }
        }

        @Specialization(guards = "isEq(opName)", limit = "2")
        static boolean doEq(@SuppressWarnings("unused") String opName, PythonAbstractNativeObject a, PythonAbstractNativeObject b,
                        @CachedLibrary("a.getPtr()") InteropLibrary aLib,
                        @CachedLibrary(limit = "3") InteropLibrary bLib) {
            return aLib.isIdentical(a.getPtr(), b.getPtr(), bLib);
        }

        @Specialization(guards = "isNe(opName)", limit = "2")
        static boolean doNe(@SuppressWarnings("unused") String opName, PythonAbstractNativeObject a, PythonAbstractNativeObject b,
                        @CachedLibrary("a.getPtr()") InteropLibrary aLib,
                        @CachedLibrary(limit = "3") InteropLibrary bLib) {
            return !aLib.isIdentical(a.getPtr(), b.getPtr(), bLib);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        static boolean execute(@SuppressWarnings("unused") String opName, PythonNativeObject a, PythonNativeObject b,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") String cachedOpName,
                        @Shared("op") @Cached(value = "findOp(opName)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPtr(), b.getPtr(), interopLibrary, importCAPISymbolNode);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        static boolean execute(@SuppressWarnings("unused") String opName, PythonNativeObject a, long b,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") String cachedOpName,
                        @Shared("op") @Cached(value = "findOp(opName)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPtr(), b, interopLibrary, importCAPISymbolNode);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        static boolean execute(@SuppressWarnings("unused") String opName, PythonNativeVoidPtr a, long b,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") String cachedOpName,
                        @Shared("op") @Cached(value = "findOp(opName)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPointerObject(), b, interopLibrary, importCAPISymbolNode);
        }

        static int findOp(String specialMethodName) {
            for (int i = 0; i < SpecialMethodNames.COMPARE_OP_COUNT; i++) {
                if (SpecialMethodNames.getCompareName(i).equals(specialMethodName)) {
                    return i;
                }
            }
            throw new RuntimeException("The special method used for Python C API pointer comparison must be a constant literal (i.e., interned) string");
        }

        static boolean isEq(String opName) {
            return SpecialMethodNames.__EQ__.equals(opName);
        }

        static boolean isNe(String opName) {
            return SpecialMethodNames.__NE__.equals(opName);
        }
    }

    @GenerateUncached
    public abstract static class PointerAddNode extends Node {
        public abstract Object execute(Object pointer, long offset);

        @Specialization
        Object add(Object pointer, long offset,
                        @Cached PCallCapiFunction callCapiFunction) {
            return callCapiFunction.call(FUN_PTR_ADD, pointer, offset);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class AllToJavaNode extends PNodeWithContext {

        final Object[] execute(Object[] args) {
            return execute(args, 0);
        }

        abstract Object[] execute(Object[] args, int offset);

        @Specialization(guards = { //
                        "args.length == cachedLength", //
                        "offset == cachedOffset", //
                        "effectiveLen(cachedLength, cachedOffset) < 5"}, //
                        limit = "5")
        @ExplodeLoop
        static Object[] cached(Object[] args, @SuppressWarnings("unused") int offset,
                        @Cached("args.length") int cachedLength,
                        @Cached("offset") int cachedOffset,
                        @Cached("createNodes(args.length)") AsPythonObjectNode[] toJavaNodes) {
            int n = cachedLength - cachedOffset;
            Object[] output = new Object[n];
            for (int i = 0; i < n; i++) {
                output[i] = toJavaNodes[i].execute(args[i + cachedOffset]);
            }
            return output;
        }

        @Specialization(replaces = "cached")
        static Object[] uncached(Object[] args, int offset,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            int len = args.length - offset;
            Object[] output = new Object[len];
            for (int i = 0; i < len; i++) {
                output[i] = toJavaNode.execute(args[i + offset]);
            }
            return output;
        }

        static int effectiveLen(int len, int offset) {
            return len - offset;
        }

        static AsPythonObjectNode[] createNodes(int n) {
            AsPythonObjectNode[] nodes = new AsPythonObjectNode[n];
            for (int i = 0; i < n; i++) {
                nodes[i] = AsPythonObjectNodeGen.create();
            }
            return nodes;
        }

        public static AllToJavaNode create() {
            return AllToJavaNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class ConvertArgsToSulongNode extends PNodeWithContext {

        public abstract void executeInto(Object[] args, int argsOffset, Object[] dest, int destOffset);

        protected static boolean isArgsOffsetPlus(int len, int off, int plus) {
            return len == off + plus;
        }

        protected static boolean isLeArgsOffsetPlus(int len, int off, int plus) {
            return len < plus + off;
        }

    }

    /**
     * Converts all arguments to native values.
     */
    public abstract static class AllToSulongNode extends ConvertArgsToSulongNode {
        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == argsOffset"})
        static void cached0(Object[] args, int argsOffset, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 1)"})
        static void cached1(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 2)"})
        static void cached2(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void cached3(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2,
                        @Cached ToBorrowedRefNode toSulongNode3) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = toSulongNode3.execute(args[argsOffset + 2]);
        }

        @Specialization(guards = {"args.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, argsOffset, 8)"}, limit = "1", replaces = {"cached0", "cached1", "cached2", "cached3"})
        @ExplodeLoop
        static void cachedLoop(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("args.length") int cachedLength,
                        @Cached ToBorrowedRefNode toSulongNode) {
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cached1", "cached2", "cached3", "cachedLoop"})
        static void uncached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode) {
            int len = args.length;
            for (int i = 0; i < len - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        public static AllToSulongNode create() {
            return AllToSulongNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    public abstract static class DirectUpcallNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object[] args);

        @Specialization(guards = "args.length == 1")
        Object upcall0(VirtualFrame frame, Object[] args,
                        @Cached("create()") CallNode callNode) {
            return callNode.execute(frame, args[0], PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 2")
        Object upcall1(VirtualFrame frame, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode) {
            return callNode.executeObject(frame, args[0], toJavaNode.execute(args[1]));
        }

        @Specialization(guards = "args.length == 3")
        Object upcall2(VirtualFrame frame, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            return callNode.executeObject(frame, args[0], converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 4")
        Object upcall3(VirtualFrame frame, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            return callNode.execute(frame, args[0], converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            return callNode.execute(frame, args[0], converted, new PKeyword[0]);
        }

        public static DirectUpcallNode create() {
            return DirectUpcallNodeGen.create();
        }
    }

    /**
     * Converts the 1st (PyObject* self) and the 2nd (PyObject* const* args) argument to native
     * values as required for {@code METH_FASTCALL}.<br/>
     * Signature:
     * {@code PyObject* meth_fastcall(PyObject* self, PyObject* const* args, Py_ssize_t nargs)}
     */
    public abstract static class FastCallArgsToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = new PySequenceArrayWrapper(args[argsOffset + 1], Long.BYTES);
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_FASTCALL_METHOD, args.length - argsOffset);
        }

        public static FastCallArgsToSulongNode create() {
            return FastCallArgsToSulongNodeGen.create();
        }
    }

    /**
     * Converts for native signature:
     * {@code PyObject* meth_fastcallWithKeywords(PyObject* self, PyObject* const* args, Py_ssize_t nargs, PyObject* kwnames)}
     */
    public abstract static class FastCallWithKeywordsArgsToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 4)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode4) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = new PySequenceArrayWrapper(args[argsOffset + 1], Long.BYTES);
            dest[destOffset + 2] = args[argsOffset + 2];
            dest[destOffset + 3] = toSulongNode4.execute(args[argsOffset + 3]);
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 4)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_FASTCALL_W_KEYWORDS_METHOD, args.length - argsOffset);
        }

        public static FastCallWithKeywordsArgsToSulongNode create() {
            return FastCallWithKeywordsArgsToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st argument as required for {@code allocfunc}, {@code getattrfunc}, and
     * {@code ssizeargfunc}.
     */
    public abstract static class BinaryFirstToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 2)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 2)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_ALLOCFUNC, args.length - argsOffset);
        }

        public static BinaryFirstToSulongNode create() {
            return BinaryFirstToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st (self/class) and the 3rd argument as required for {@code setattrfunc},
     * {@code ssizeobjargproc}.
     */
    public abstract static class TernaryFirstThirdToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode3) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = toSulongNode3.execute(args[argsOffset + 2]);
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, args.length - argsOffset);
        }

        public static TernaryFirstThirdToSulongNode create() {
            return TernaryFirstThirdToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st (self/class) and the 2rd argument as required for {@code richcmpfunc}.
     */
    public abstract static class TernaryFirstSecondToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, args.length - argsOffset);
        }

        public static TernaryFirstSecondToSulongNode create() {
            return TernaryFirstSecondToSulongNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class CextUpcallNode extends PNodeWithContext {
        public static CextUpcallNode create() {
            return CextUpcallNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, Object cextModule, Object[] args);

        @Specialization(guards = "args.length == 1")
        Object upcall0(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 2")
        Object upcall1(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.executeObject(frame, callable, toJavaNode.execute(args[1]));
        }

        @Specialization(guards = "args.length == 3")
        Object upcall2(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 4")
        Object upcall3(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, converted, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Specializes on the arity of the call and tries to do a builtin call if possible, otherwise a
     * generic call is done. The arguments array must have at least two element: {@code args[0]} is
     * the receiver (e.g. the module) and {@code args[1]} is the member to call.
     */
    public abstract static class ObjectUpcallNode extends PNodeWithContext {
        public static ObjectUpcallNode create() {
            return ObjectUpcallNodeGen.create();
        }

        /**
         * The {@code args} array must contain the receiver at {@code args[0]} and the member at
         * {@code args[1]}.
         */
        public abstract Object execute(VirtualFrame frame, Object[] args);

        @Specialization(guards = "args.length == 2")
        Object upcall0(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.call(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall1(VirtualFrame frame, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Cached CExtNodes.AsPythonObjectNode argToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.call(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, argToJavaNode.execute(args[2]));
        }

        @Specialization(guards = "args.length == 4")
        Object upcall2(VirtualFrame frame, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.call(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 5")
        Object upcall3(VirtualFrame frame, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.call(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            // we needs at least a receiver and a member name
            assert args.length >= 2;
            Object[] converted = allToJavaNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.call(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Converts a Python object to a
     * {@link com.oracle.graal.python.builtins.objects.complex.PComplex} .<br/>
     * This node is, for example, used to implement {@code PyComplex_AsCComplex} and does coercion
     * and may raise a Python exception if coercion fails.
     */
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class AsNativeComplexNode extends PNodeWithContext {
        public abstract PComplex execute(boolean arg);

        public abstract PComplex execute(int arg);

        public abstract PComplex execute(long arg);

        public abstract PComplex execute(double arg);

        public abstract PComplex execute(Object arg);

        @Specialization
        PComplex doPComplex(PComplex value) {
            return value;
        }

        @Specialization
        PComplex doBoolean(boolean value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value ? 1.0 : 0.0, 0.0);
        }

        @Specialization
        PComplex doInt(int value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value, 0.0);
        }

        @Specialization
        PComplex doLong(long value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value, 0.0);
        }

        @Specialization
        PComplex doDouble(double value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value, 0.0);
        }

        @Specialization
        PComplex doPInt(PInt value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value.doubleValue(), 0.0);
        }

        @Specialization
        PComplex doPFloat(PFloat value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value.getValue(), 0.0);
        }

        @Specialization(replaces = {"doPComplex", "doBoolean", "doInt", "doLong", "doDouble", "doPInt", "doPFloat"})
        PComplex runGeneric(Object value,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached LookupAndCallUnaryDynamicNode callFloatFunc,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            Object result = callFloatFunc.executeObject(value, __COMPLEX__);
            // TODO(fa) according to CPython's 'PyComplex_AsCComplex', they still allow subclasses
            // of PComplex
            if (result == PNone.NO_VALUE) {
                throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.COMPLEX_RETURNED_NON_COMPLEX, value);
            } else if (result instanceof PComplex) {
                return (PComplex) result;
            }
            return factory.createComplex(lib.asJavaDouble(value), 0.0);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Casts a Python object to a Java double value without doing any coercion, i.e., it does not
     * call any magic method like {@code __float__}.<br/>
     * The semantics is like a Java type cast and therefore lossy.<br/>
     * As an optimization, this node can also unwrap {@code PrimitiveNativeWrapper} instances to
     * avoid eager and explicit conversion.
     */
    @GenerateUncached
    public abstract static class CastToJavaDoubleNode extends PNodeWithContext {
        public abstract double execute(boolean arg);

        public abstract double execute(int arg);

        public abstract double execute(long arg);

        public abstract double execute(double arg);

        public abstract double execute(Object arg);

        @Specialization
        double run(boolean value) {
            return value ? 1.0 : 0.0;
        }

        @Specialization
        double run(int value) {
            return value;
        }

        @Specialization
        double run(long value) {
            return value;
        }

        @Specialization
        double run(double value) {
            return value;
        }

        @Specialization
        double run(PInt value) {
            return value.doubleValue();
        }

        @Specialization
        double run(PFloat value) {
            return value.getValue();
        }

        @Specialization(guards = "!object.isDouble()")
        double doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        double doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getDouble();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Casts a Python object to a Java long value without doing any coercion, i.e., it does not call
     * any magic method like {@code __index__} or {@code __int__}.<br/>
     * The semantics is like a Java type cast and therefore lossy.<br/>
     * As an optimization, this node can also unwrap {@code PrimitiveNativeWrapper} instances to
     * avoid eager and explicit conversion.
     */
    @GenerateUncached
    public abstract static class CastToNativeLongNode extends PNodeWithContext {
        public abstract long execute(boolean arg);

        public abstract long execute(byte arg);

        public abstract long execute(int arg);

        public abstract long execute(long arg);

        public abstract long execute(double arg);

        public abstract Object execute(Object arg);

        @Specialization(guards = "value.length() == 1")
        static long doString(String value) {
            return value.charAt(0);
        }

        @Specialization
        static long doBoolean(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        static long doByte(byte value) {
            return value;
        }

        @Specialization
        static long doInt(int value) {
            return value;
        }

        @Specialization
        static long doLong(long value) {
            return value;
        }

        @Specialization
        static long doDouble(double value) {
            return (long) value;
        }

        @Specialization
        static long doPInt(PInt value) {
            return value.longValue();
        }

        @Specialization
        static long doPFloat(PFloat value) {
            return (long) value.getValue();
        }

        @Specialization
        static Object doPythonNativeVoidPtr(PythonNativeVoidPtr object) {
            return object.getPointerObject();
        }

        @Specialization(guards = "!object.isDouble()")
        static long doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static long doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return (long) object.getDouble();
        }

        @Specialization(limit = "1")
        static Object run(PythonNativeWrapper value,
                        @CachedLibrary("value") PythonNativeWrapperLibrary lib,
                        @Cached CastToNativeLongNode recursive) {
            // TODO(fa) this specialization should eventually go away
            return recursive.execute(lib.getDelegate(value));
        }
    }

    /**
     * Converts a Python object (i.e. {@code PyObject*}) to a C integer value ({@code int} or
     * {@code long}).<br/>
     * This node is used to implement {@code PyLong_AsLong} or similar C API functions and does
     * coercion and may raise a Python exception if coercion fails.
     */
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class AsNativePrimitiveNode extends Node {

        public final int toInt32(Object value, boolean exact) {
            return (int) execute(value, 1, 4, exact);
        }

        public final int toUInt32(Object value, boolean exact) {
            return (int) execute(value, 0, 4, exact);
        }

        public final long toInt64(Object value, boolean exact) {
            return (long) execute(value, 1, 8, exact);
        }

        public final long toUInt64(Object value, boolean exact) {
            return (long) execute(value, 0, 8, exact);
        }

        public abstract Object execute(byte value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(int value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(long value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(Object value, int signed, int targetTypeSize, boolean exact);

        @Specialization(guards = "targetTypeSize == 4")
        @SuppressWarnings("unused")
        static int doIntToInt32(int obj, int signed, int targetTypeSize, boolean exact) {
            // n.b. even if an unsigned is requested, it does not matter because the unsigned
            // interpretation is done in C code.
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static long doIntToInt64(int obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doIntToOther(int obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        @SuppressWarnings("unused")
        static int doLongToInt32(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static long doLongToInt64(long obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static Object doVoidPtrToI64(PythonNativeVoidPtr obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doPInt(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed != 0"})
        static int doPIntToInt32Signed(PInt obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") int targetTypeSize, @SuppressWarnings("unused") boolean exact,
                        @Exclusive @Cached BranchProfile errorProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return obj.intValueExact();
            } catch (OverflowException e) {
                // fall through
            }
            errorProfile.enter();
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed == 0"})
        static int doPIntToInt32NotSigned(PInt obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") int targetTypeSize, boolean exact,
                        @Exclusive @Cached BranchProfile errorProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (!exact || obj.bitCount() <= 32) {
                return obj.intValue();
            }
            errorProfile.enter();
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 8", "signed != 0"})
        static long doPIntToInt64Signed(PInt obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") int targetTypeSize, @SuppressWarnings("unused") boolean exact,
                        @Exclusive @Cached BranchProfile errorProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return obj.longValueExact();
            } catch (OverflowException e) {
                // fall through
            }
            errorProfile.enter();
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 8", "signed == 0"})
        static long doPIntToInt64NotSigned(PInt obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") int targetTypeSize, boolean exact,
                        @Exclusive @Cached BranchProfile errorProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (!exact || obj.bitCount() <= 64) {
                return obj.longValue();
            }
            errorProfile.enter();
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        static int doPIntToInt32Lossy(PInt obj, int signed, int targetTypeSize, boolean exact) {
            return obj.intValue();
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 8"})
        @SuppressWarnings("unused")
        static long doPIntToInt64Lossy(PInt obj, int signed, int targetTypeSize, boolean exact) {
            return obj.longValue();
        }

        @Specialization(guards = {"isIntegerType(obj)", "targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doError(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(replaces = {"doIntToInt32", "doIntToInt64", "doIntToOther", "doLongToInt32", "doLongToInt64", "doVoidPtrToI64", "doPIntToInt32Signed", "doPIntToInt32NotSigned",
                        "doPIntToInt64Signed", "doPIntToInt64NotSigned"})
        static Object doGeneric(Object obj, @SuppressWarnings("unused") int signed, int targetTypeSize, boolean exact,
                        @Cached LookupAndCallUnaryDynamicNode callIndexNode,
                        @Cached LookupAndCallUnaryDynamicNode callIntNode,
                        @Cached AsNativePrimitiveNode recursive,
                        @Exclusive @Cached BranchProfile noIntProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {

            Object result = callIndexNode.executeObject(obj, SpecialMethodNames.__INDEX__);
            if (result == PNone.NO_VALUE) {
                result = callIntNode.executeObject(obj, SpecialMethodNames.__INT__);
                if (result == PNone.NO_VALUE) {
                    noIntProfile.enter();
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, result);
                }
            }
            // n.b. this check is important to avoid endless recursions; it will ensure that
            // 'doGeneric' is not triggered in the recursive node
            if (!(isIntegerType(result))) {
                throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
            }
            return recursive.execute(result, signed, targetTypeSize, exact);
        }

        static boolean isIntegerType(Object obj) {
            return PGuards.isInteger(obj) || PGuards.isPInt(obj) || obj instanceof PythonNativeVoidPtr;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PCallCapiFunction extends Node {

        public final Object call(String name, Object... args) {
            return execute(name, args);
        }

        public abstract Object execute(String name, Object[] args);

        @Specialization
        static Object doIt(String name, Object[] args,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCExtSymbolNode.execute(context.getCApiContext(), name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAPI_SYM_NOT_CALLABLE, name);
            }
        }

        public static PCallCapiFunction create() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.create();
        }

        public static PCallCapiFunction getUncached() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.getUncached();
        }
    }

    /**
     * Simple enum to abstract over common error indication values used in C extensions. We use this
     * enum instead of concrete values to be able to safely share them between contexts.
     */
    public enum MayRaiseErrorResult {
        NATIVE_NULL,
        NONE,
        INT,
        FLOAT
    }

    /**
     * A fake-expression node that wraps an expression node with a {@code try-catch} and any catched
     * Python exception will be transformed to native and the pre-defined error result (specified
     * with enum {@link MayRaiseErrorResult}) will be returned.
     */
    public static final class MayRaiseNode extends ExpressionNode {
        @Child private ExpressionNode wrappedBody;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        @Child private GetNativeNullNode getNativeNullNode;

        private final MayRaiseErrorResult errorResult;

        MayRaiseNode(ExpressionNode wrappedBody, MayRaiseErrorResult errorResult) {
            this.wrappedBody = wrappedBody;
            this.errorResult = errorResult;
        }

        public static MayRaiseNode create(ExpressionNode nodeToWrap, MayRaiseErrorResult errorResult) {
            return new MayRaiseNode(nodeToWrap, errorResult);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return wrappedBody.execute(frame);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return getErrorResult();
            }
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }

        private Object getErrorResult() {
            switch (errorResult) {
                case INT:
                    return -1;
                case FLOAT:
                    return -1.0;
                case NONE:
                    return PNone.NONE;
                case NATIVE_NULL:
                    if (getNativeNullNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getNativeNullNode = insert(GetNativeNullNodeGen.create());
                    }
                    return getNativeNullNode.execute();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class IsPointerNode extends com.oracle.graal.python.nodes.PNodeWithContext {

        public abstract boolean execute(PythonNativeWrapper obj);

        @Specialization(assumptions = {"singleContextAssumption()", "nativeObjectsAllManagedAssumption()"})
        static boolean doFalse(@SuppressWarnings("unused") PythonNativeWrapper obj) {
            return false;
        }

        @Specialization(guards = "lib.isNative(obj)", limit = "1")
        @SuppressWarnings("unused")
        static boolean doNative(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib) {
            return true;
        }

        @Specialization(limit = "1", replaces = {"doFalse", "doNative"})
        static boolean doGeneric(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib) {
            return lib.isNative(obj);
        }

        protected static Assumption nativeObjectsAllManagedAssumption() {
            return PythonLanguage.getContext().getNativeObjectsAllManagedAssumption();
        }

        public static IsPointerNode create() {
            return IsPointerNodeGen.create();
        }

        public static IsPointerNode getUncached() {
            return IsPointerNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    @GenerateUncached
    @TypeSystemReference(PythonTypes.class)
    public abstract static class GetTypeMemberNode extends PNodeWithContext {
        public abstract Object execute(Object obj, NativeMember nativeMember);

        /*
         * A note about the logic here, and why this is fine: the cachedObj is from a particular
         * native context, so we can be sure that the "nativeClassStableAssumption" (which is
         * per-context) is from the context in which this native object was created.
         */
        @Specialization(guards = {"lib.isIdentical(cachedObj, obj, lib)", "memberName == cachedMemberName"}, //
                        limit = "1", //
                        assumptions = {"getNativeClassStableAssumption(cachedObj)", "singleContextAssumption()"})
        public Object doCachedObj(@SuppressWarnings("unused") PythonAbstractNativeObject obj, @SuppressWarnings("unused") NativeMember memberName,
                        @Cached("obj") @SuppressWarnings("unused") PythonAbstractNativeObject cachedObj,
                        @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached("memberName") @SuppressWarnings("unused") NativeMember cachedMemberName,
                        @Cached("doSlowPath(obj, memberName)") Object result) {
            return result;
        }

        @Specialization(guards = {"lib.hasMembers(object.getPtr())", "member.getType() == cachedMemberType"}, //
                        replaces = "doCachedObj", limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getByMember(PythonAbstractNativeObject object, NativeMember member,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Cached("member.getType()") @SuppressWarnings("unused") NativeMemberType cachedMemberType,
                        @Cached(value = "createForMember(member)", uncached = "getUncachedForMember(member)") ToJavaBaseNode toJavaNode)
                        throws UnknownIdentifierException, UnsupportedMessageException {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return toJavaNode.execute(lib.readMember(object.getPtr(), member.getMemberName()));
        }

        @Specialization(guards = {"!lib.hasMembers(object.getPtr())", "member.getType() == cachedMemberType"}, //
                        replaces = {"doCachedObj", "getByMember"}, limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getByMemberAttachType(PythonAbstractNativeObject object, NativeMember member,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Cached("member.getType()") @SuppressWarnings("unused") NativeMemberType cachedMemberType,
                        @Exclusive @Cached PCallCapiFunction callGetObTypeNode,
                        @Exclusive @Cached CExtNodes.GetLLVMType getLLVMType,
                        @Cached(value = "createForMember(member)", uncached = "getUncachedForMember(member)") ToJavaBaseNode toJavaNode)
                        throws UnknownIdentifierException, UnsupportedMessageException {
            Object typedPtr = callGetObTypeNode.call(FUN_POLYGLOT_FROM_TYPED, object.getPtr(), getLLVMType.execute(CApiContext.LLVMType.PyTypeObject));
            return toJavaNode.execute(lib.readMember(typedPtr, member.getMemberName()));
        }

        @Specialization(guards = "memberName == cachedMemberName", limit = "1", replaces = {"doCachedObj", "getByMember", "getByMemberAttachType"})
        static Object doCachedMember(Object self, @SuppressWarnings("unused") NativeMember memberName,
                        @SuppressWarnings("unused") @Cached("memberName") NativeMember cachedMemberName,
                        @Cached("getterFuncName(memberName)") String getterName,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Cached(value = "createForMember(memberName)", uncached = "getUncachedForMember(memberName)") ToJavaBaseNode toJavaNode,
                        @Shared("callMemberGetterNode") @Cached PCallCapiFunction callMemberGetterNode) {
            return toJavaNode.execute(callMemberGetterNode.call(getterName, toSulong.execute(self)));
        }

        @Specialization(replaces = {"doCachedObj", "getByMember", "getByMemberAttachType", "doCachedMember"})
        static Object doGeneric(Object self, NativeMember memberName,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Cached ToJavaNode toJavaNode,
                        @Cached CharPtrToJavaNode charPtrToJavaNode,
                        @Cached VoidPtrToJavaNode voidPtrToJavaNode,
                        @Shared("callMemberGetterNode") @Cached PCallCapiFunction callMemberGetterNode) {
            Object value = callMemberGetterNode.call(getterFuncName(memberName), toSulong.execute(self));
            switch (memberName.getType()) {
                case OBJECT:
                    return toJavaNode.execute(value);
                case CSTRING:
                    return charPtrToJavaNode.execute(value);
                case PRIMITIVE:
                case POINTER:
                    return voidPtrToJavaNode.execute(value);
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        static Object doSlowPath(Object obj, NativeMember memberName) {
            String getterFuncName = getterFuncName(memberName);
            return getUncachedForMember(memberName).execute(PCallCapiFunction.getUncached().call(getterFuncName, ToSulongNode.getUncached().execute(obj)));
        }

        @TruffleBoundary
        static String getterFuncName(NativeMember memberName) {
            String name = "get_" + memberName.getMemberName();
            assert NativeCAPISymbols.isValid(name) : "invalid native member getter function " + name;
            return name;
        }

        static ToJavaBaseNode createForMember(NativeMember member) {
            switch (member.getType()) {
                case OBJECT:
                    return ToJavaNodeGen.create();
                case CSTRING:
                    return CharPtrToJavaNodeGen.create();
                case PRIMITIVE:
                case POINTER:
                    return VoidPtrToJavaNodeGen.create();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        static ToJavaBaseNode getUncachedForMember(NativeMember member) {
            switch (member.getType()) {
                case OBJECT:
                    return ToJavaNodeGen.getUncached();
                case CSTRING:
                    return CharPtrToJavaNodeGen.getUncached();
                case PRIMITIVE:
                case POINTER:
                    return VoidPtrToJavaNodeGen.getUncached();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected Assumption getNativeClassStableAssumption(PythonNativeClass clazz) {
            return PythonLanguage.getContext().getNativeClassStableAssumption(clazz, true).getAssumption();
        }

        public static GetTypeMemberNode create() {
            return GetTypeMemberNodeGen.create();
        }

        public static GetTypeMemberNode getUncached() {
            return GetTypeMemberNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GetNativeNullNode extends Node {

        public abstract Object execute(Object module);

        public final Object execute() {
            return execute(null);
        }

        @Specialization(guards = "module != null")
        static Object getNativeNullWithModule(Object module,
                        @Shared("readAttrNode") @Cached ReadAttributeFromObjectNode readAttrNode) {
            Object wrapper = readAttrNode.execute(module, PythonCextBuiltins.NATIVE_NULL);
            assert wrapper instanceof PythonNativeNull;
            return wrapper;
        }

        @Specialization(guards = "module == null")
        static Object getNativeNullWithoutModule(@SuppressWarnings("unused") Object module,
                        @Shared("readAttrNode") @Cached ReadAttributeFromObjectNode readAttrNode,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            PythonModule pythonCextModule = context.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT);
            Object wrapper = readAttrNode.execute(pythonCextModule, PythonCextBuiltins.NATIVE_NULL);
            assert wrapper instanceof PythonNativeNull;
            return wrapper;
        }

    }

    /**
     * Use this node to lookup a native type member like {@code tp_alloc}.<br>
     * <p>
     * This node basically implements the native member inheritance that is done by
     * {@code inherit_special} or other code in {@code PyType_Ready}.
     * </p>
     * <p>
     * Since it may be that a managed types needs to emulate such members but there is no
     * corresponding Python attribute (e.g. {@code tp_alloc}), such members are stored as hidden
     * keys on the managed type. However, the MRO may contain native types and in this case, we need
     * to access the native member.
     * </p>
     */
    @GenerateUncached
    public abstract static class LookupNativeMemberInMRONode extends Node {

        public abstract Object execute(Object cls, NativeMember nativeMemberName, Object managedMemberName);

        @Specialization
        static Object doSingleContext(Object cls, NativeMember nativeMemberName, Object managedMemberName,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttrNode,
                        @Cached GetTypeMemberNode getTypeMemberNode) {

            MroSequenceStorage mroStorage = getMroNode.execute(cls);
            int n = lenNode.execute(mroStorage);

            for (int i = 0; i < n; i++) {
                PythonAbstractClass mroCls = (PythonAbstractClass) getItemNode.execute(mroStorage, i);
                Object result;
                if (PGuards.isManagedClass(mroCls)) {
                    result = readAttrNode.execute(mroCls, managedMemberName);
                } else {
                    assert PGuards.isNativeClass(mroCls) : "invalid class inheritance structure; expected native class";
                    result = getTypeMemberNode.execute(mroCls, nativeMemberName);
                }
                if (result != PNone.NO_VALUE) {
                    return result;
                }
            }

            return PNone.NO_VALUE;
        }
    }

    /**
     * Use this node to transform an exception to native if a Python exception was thrown during an
     * upcall and before returning to native code. This node will reify the exception appropriately
     * and register the exception as the current exception.
     */
    @GenerateUncached
    public abstract static class TransformExceptionToNativeNode extends Node {

        public abstract void execute(Frame frame, PException e);

        public final void execute(PException e) {
            execute(null, e);
        }

        @Specialization
        static void setCurrentException(Frame frame, PException e,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame).markAsEscaped();
            context.setCurrentException(e);
        }
    }

    @GenerateUncached
    public abstract static class PRaiseNativeNode extends Node {

        public final int raiseInt(Frame frame, int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(frame, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, Object errorValue, Object errType, String format, Object... arguments) {
            return execute(frame, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(null, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, Object errorValue, Object errType, String format, Object[] arguments);

        public abstract int executeInt(Frame frame, int errorValue, Object errType, String format, Object[] arguments);

        @Specialization
        static int doInt(Frame frame, int errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, Object errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        public static void raiseNative(Frame frame, Object errType, String format, Object[] arguments, PRaiseNode raiseNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, p);
            }
        }
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class AddRefCntNode extends PNodeWithContext {

        public abstract Object execute(Object object, long value);

        public final Object inc(Object object) {
            return execute(object, 1);
        }

        @Specialization
        static Object doNativeWrapper(PythonNativeWrapper nativeWrapper, long value) {
            assert value >= 0 : "adding negative reference count; dealloc might not happen";
            nativeWrapper.setRefCount(nativeWrapper.getRefCount() + value);
            return nativeWrapper;
        }

        @Specialization(guards = {"!isNativeWrapper(object)", "lib.hasMembers(object)"}, //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class, UnsupportedTypeException.class, CannotCastException.class}, //
                        limit = "1")
        static Object doNativeObjectByMember(Object object, long value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("object") InteropLibrary lib) throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException, CannotCastException {
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                assert value >= 0 : "adding negative reference count; dealloc might not happen";
                cApiContext.checkAccess(object, lib);
                long refCnt = castToJavaLongNode.execute(lib.readMember(object, OB_REFCNT.getMemberName()));
                lib.writeMember(object, OB_REFCNT.getMemberName(), refCnt + value);
            }
            return object;
        }

        @Specialization(guards = "!isNativeWrapper(object)", limit = "2", replaces = "doNativeObjectByMember")
        static Object doNativeObject(Object object, long value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callAddRefCntNode,
                        @CachedLibrary("object") InteropLibrary lib) {
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                assert value >= 0 : "adding negative reference count; dealloc might not happen";
                cApiContext.checkAccess(object, lib);
                callAddRefCntNode.call(NativeCAPISymbols.FUN_ADDREF, object, value);
            }
            return object;
        }
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class SubRefCntNode extends PNodeWithContext {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(SubRefCntNode.class);

        public final long dec(Object object) {
            return execute(object, 1);
        }

        public abstract long execute(Object object, long value);

        @Specialization
        static long doNativeWrapper(PythonNativeWrapper nativeWrapper, long value,
                        @Cached FreeNode freeNode,
                        @Cached BranchProfile negativeProfile) {
            long refCount = nativeWrapper.getRefCount() - value;
            nativeWrapper.setRefCount(refCount);
            if (refCount == 0) {
                // 'freeNode' acts as a branch profile
                freeNode.execute(nativeWrapper);
            } else if (refCount < 0) {
                negativeProfile.enter();
                LOGGER.severe(() -> "native wrapper has negative ref count: " + nativeWrapper);
            }
            return refCount;
        }

        @Specialization(guards = "!isNativeWrapper(object)", limit = "2")
        static long doNativeObject(Object object, long value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callAddRefCntNode,
                        @CachedLibrary("object") InteropLibrary lib) {
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                cApiContext.checkAccess(object, lib);
                long newRefcnt = (long) callAddRefCntNode.call(NativeCAPISymbols.FUN_SUBREF, object, value);
                if (context.getOption(PythonOptions.TraceNativeMemory) && newRefcnt < 0) {
                    LOGGER.severe(() -> "object has negative ref count: " + CApiContext.asHex(object));
                }
                return newRefcnt;
            }
            return 1;
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class ClearNativeWrapperNode extends Node {

        public abstract void execute(Object delegate, PythonNativeWrapper nativeWrapper);

        @Specialization(guards = "!isPrimitiveNativeWrapper(nativeWrapper)")
        static void doPythonAbstractObject(PythonAbstractObject delegate, PythonNativeWrapper nativeWrapper,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile) {
            // For non-temporary wrappers (all wrappers that need to preserve identity):
            // If this assertion fails, it indicates that the native code still uses a free'd native
            // wrapper.
            // TODO(fa): explicitly mark native wrappers to be identity preserving
            assert !(nativeWrapper instanceof PythonObjectNativeWrapper) || delegate.getNativeWrapper() == nativeWrapper : "inconsistent native wrappers";
            delegate.clearNativeWrapper(hasHandleValidAssumptionProfile);
        }

        @Specialization(guards = "delegate == null")
        static void doPrimitiveNativeWrapper(@SuppressWarnings("unused") Object delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            assert !isSmallIntegerWrapperSingleton(contextRef, nativeWrapper) : "clearing primitive native wrapper singleton of small integer";
            Assumption handleValidAssumption = nativeWrapper.getHandleValidAssumption();
            if (hasHandleValidAssumptionProfile.profile(handleValidAssumption != null)) {
                PythonNativeWrapper.invalidateAssumption(handleValidAssumption);
            }
        }

        @Specialization(guards = "delegate != null")
        static void doPrimitiveNativeWrapperMaterialized(PythonAbstractObject delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (profile.profile(delegate.getNativeWrapper() == nativeWrapper)) {
                assert !isSmallIntegerWrapperSingleton(contextRef, nativeWrapper) : "clearing primitive native wrapper singleton of small integer";
                delegate.clearNativeWrapper(hasHandleValidAssumptionProfile);
            }
        }

        @Specialization(guards = {"delegate != null", "!isAnyPythonObject(delegate)"})
        static void doOther(@SuppressWarnings("unused") Object delegate, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper) {
            assert !isPrimitiveNativeWrapper(nativeWrapper);
            // ignore
        }

        static boolean isPrimitiveNativeWrapper(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper instanceof PrimitiveNativeWrapper;
        }

        private static boolean isSmallIntegerWrapperSingleton(ContextReference<PythonContext> contextRef, PrimitiveNativeWrapper nativeWrapper) {
            return CApiGuards.isSmallIntegerWrapper(nativeWrapper) && ToSulongNode.doLongSmall(null, nativeWrapper.getLong(), contextRef) == nativeWrapper;
        }

    }

    @GenerateUncached
    public abstract static class GetRefCntNode extends PNodeWithContext {

        public final long execute(Object ptrObject) {
            return execute(CApiContext.LAZY_CONTEXT, ptrObject);
        }

        public abstract long execute(CApiContext cApiContext, Object ptrObject);

        @Specialization(guards = "!isLazyContext(cApiContext)", limit = "2", rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static long doNativeObjectTypedWithContext(CApiContext cApiContext, Object ptrObject,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) throws UnknownIdentifierException, UnsupportedMessageException {
            if (!lib.isNull(ptrObject)) {
                boolean haveCApiContext = cApiContext != null;
                if (haveCApiContext) {
                    cApiContext.checkAccess(ptrObject, lib);
                }

                // directly reading the member is only possible if the pointer object is typed but
                // if so, it is the faster way
                if (lib.hasMembers(ptrObject)) {
                    return castToJavaLongNode.execute(lib.readMember(ptrObject, OB_REFCNT.getMemberName()));
                }
                if (haveCApiContext) {
                    return castToJavaLongNode.execute(callGetObRefCntNode.call(NativeCAPISymbols.FUN_GET_OB_REFCNT, ptrObject));
                }
            }
            return 0;
        }

        @Specialization(guards = "!isLazyContext(cApiContext)", limit = "2", replaces = "doNativeObjectTypedWithContext")
        static long doNativeObjectWithContext(CApiContext cApiContext, Object ptrObject,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) {
            if (!lib.isNull(ptrObject) && cApiContext != null) {
                cApiContext.checkAccess(ptrObject, lib);
                return castToJavaLongNode.execute(callGetObRefCntNode.call(NativeCAPISymbols.FUN_GET_OB_REFCNT, ptrObject));
            }
            return 0;
        }

        @Specialization(limit = "2", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class}, //
                        replaces = {"doNativeObjectTypedWithContext", "doNativeObjectWithContext"})
        static long doNativeObjectTyped(@SuppressWarnings("unused") CApiContext cApiContext, Object ptrObject,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) throws UnknownIdentifierException, UnsupportedMessageException {
            return doNativeObjectTypedWithContext(context.getCApiContext(), ptrObject, callGetObRefCntNode, lib, castToJavaLongNode);
        }

        @Specialization(limit = "2", replaces = {"doNativeObjectTypedWithContext", "doNativeObjectWithContext", "doNativeObjectTyped"})
        static long doNativeObject(@SuppressWarnings("unused") CApiContext cApiContext, Object ptrObject,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) {
            return doNativeObjectWithContext(context.getCApiContext(), ptrObject, callGetObRefCntNode, lib, castToJavaLongNode);
        }

        static boolean isLazyContext(CApiContext cApiContext) {
            return CApiContext.LAZY_CONTEXT == cApiContext;
        }
    }

    @GenerateUncached
    public abstract static class ResolveHandleNode extends Node {

        public abstract Object execute(Object pointerObject);

        public abstract Object executeLong(long pointer);

        @Specialization(limit = "3", //
                        guards = {"cachedPointer == pointer", "cachedValue != null"}, //
                        assumptions = "singleContextAssumption()", //
                        rewriteOn = InvalidAssumptionException.class)
        static PythonNativeWrapper resolveLongCached(@SuppressWarnings("unused") long pointer,
                        @Cached("pointer") @SuppressWarnings("unused") long cachedPointer,
                        @Cached("resolveHandleUncached(pointer)") PythonNativeWrapper cachedValue,
                        @Cached("getHandleValidAssumption(cachedValue)") Assumption associationValidAssumption) throws InvalidAssumptionException {
            associationValidAssumption.check();
            return cachedValue;
        }

        @Specialization(limit = "3", //
                        guards = {"isSame(lib, cachedPointerObject, pointerObject)", "cachedValue != null"}, //
                        assumptions = "singleContextAssumption()", //
                        rewriteOn = InvalidAssumptionException.class)
        static PythonNativeWrapper resolveObjectCached(@SuppressWarnings("unused") Object pointerObject,
                        @Cached("pointerObject") @SuppressWarnings("unused") Object cachedPointerObject,
                        @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached("resolveHandleUncached(pointerObject)") PythonNativeWrapper cachedValue,
                        @Cached("getHandleValidAssumption(cachedValue)") Assumption associationValidAssumption) throws InvalidAssumptionException {
            associationValidAssumption.check();
            return cachedValue;
        }

        @Specialization(replaces = {"resolveLongCached", "resolveObjectCached"})
        static Object resolveGeneric(Object pointerObject,
                        @Cached PCallCapiFunction callTruffleCannotBeHandleNode,
                        @Cached PCallCapiFunction callTruffleManagedFromHandleNode) {
            if (((boolean) callTruffleCannotBeHandleNode.call(NativeCAPISymbols.FUN_POINTS_TO_HANDLE_SPACE, pointerObject))) {
                return callTruffleManagedFromHandleNode.call(NativeCAPISymbols.FUN_RESOLVE_HANDLE, pointerObject);
            }
            // In this case, it cannot be a handle so we can just return the pointer object. It
            // could, of course, still be a native pointer.
            return pointerObject;
        }

        static PythonNativeWrapper resolveHandleUncached(Object pointerObject) {
            CompilerAsserts.neverPartOfCompilation();
            if (((boolean) PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_POINTS_TO_HANDLE_SPACE, pointerObject))) {
                Object resolved = PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_RESOLVE_HANDLE, pointerObject);
                if (resolved instanceof PythonNativeWrapper) {
                    return (PythonNativeWrapper) resolved;
                }
            }
            // In this case, it cannot be a handle so we return 'null' to indicate that it should
            // not be cached.
            return null;
        }

        static boolean isSame(InteropLibrary lib, Object left, Object right) {
            return lib.isIdentical(left, right, lib);
        }

        static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        static Assumption getHandleValidAssumption(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper.ensureHandleValidAssumption();
        }
    }

    /**
     * Depending on the object's type, the size may need to be computed in very different ways. E.g.
     * any PyVarObject usually returns the number of contained elements.
     */
    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    abstract static class ObSizeNode extends Node {

        public abstract long execute(Object object);

        @Specialization
        static long doInteger(@SuppressWarnings("unused") int object,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return doLong(object, context);
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") long object,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            long t = PInt.abs(object);
            int sign = object < 0 ? -1 : 1;
            int size = 0;
            while (t != 0) {
                ++size;
                t >>= context.getCApiContext().getPyLongBitsInDigit();
            }
            return size * sign;
        }

        @Specialization
        static long doPInt(PInt object,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return ((PInt.bitLength(object.abs()) - 1) / context.getCApiContext().getPyLongBitsInDigit() + 1) * (object.isNegative() ? -1 : 1);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = "isFallback(object)")
        static long doOther(Object object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            try {
                return lib.length(object);
            } catch (PException e) {
                return -1;
            }
        }

        static boolean isFallback(Object object) {
            return !(object instanceof PInt || object instanceof Integer || object instanceof Long);
        }
    }

    @GenerateUncached
    public abstract static class GetLLVMType extends Node {
        public abstract TruffleObject execute(LLVMType llvmType);

        @Specialization(guards = "llvmType == cachedType", limit = "typeCount()")
        static TruffleObject doGeneric(@SuppressWarnings("unused") LLVMType llvmType,
                        @Cached("llvmType") LLVMType cachedType,
                        @CachedContext(PythonLanguage.class) PythonContext context) {

            CApiContext cApiContext = context.getCApiContext();
            TruffleObject llvmTypeID = cApiContext.getLLVMTypeID(cachedType);

            // TODO(fa): get rid of lazy initialization for better sharing
            if (llvmTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                String getterFunctionName = LLVMType.getGetterFunctionName(llvmType);
                llvmTypeID = (TruffleObject) PCallCapiFunction.getUncached().call(getterFunctionName);
                cApiContext.setLLVMTypeID(cachedType, llvmTypeID);
            }
            return llvmTypeID;
        }

        static int typeCount() {
            CompilerAsserts.neverPartOfCompilation();
            return LLVMType.values().length;
        }
    }

    @GenerateUncached
    public abstract static class UnicodeFromFormatNode extends Node {
        private static Pattern pattern;

        private static Matcher match(String formatStr) {
            if (pattern == null) {
                pattern = Pattern.compile("%(?<flags>[-\\+ #0])?(?<width>\\d+)?(\\.(?<prec>\\d+))?(?<len>(l|ll|z))?(?<spec>[%cduixsAUVSR])");
            }
            return pattern.matcher(formatStr);
        }

        public abstract Object execute(String format, Object vaList);

        @Specialization
        @TruffleBoundary
        Object doGeneric(String format, Object vaList,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {

            // helper nodes
            GetVaArgsNode getVaArgsNode = GetVaArgsNodeGen.getUncached();
            ToJavaNode toJavaNode = ToJavaNodeGen.getUncached();
            CastToJavaStringNode castToJavaStringNode = CastToJavaStringNodeGen.getUncached();
            FromCharPointerNode fromCharPointerNode = FromCharPointerNodeGen.getUncached();
            InteropLibrary interopLibrary = InteropLibrary.getUncached();
            PRaiseNode raiseNode = PRaiseNode.getUncached();

            // set the encapsulating node reference to get a precise error position
            EncapsulatingNodeReference current = EncapsulatingNodeReference.getCurrent();
            current.set(this);
            StringBuilder result = new StringBuilder();
            int vaArgIdx = 0;
            try {
                Matcher matcher = match(format);
                int cur = 0;
                while (matcher.find(cur)) {
                    // not all combinations are valid
                    boolean valid = false;

                    // add anything before the match
                    result.append(format, cur, matcher.start());

                    cur = matcher.end();

                    String spec = matcher.group("spec");
                    String len = matcher.group("len");
                    int prec = getPrec(matcher.group("prec"));
                    assert spec.length() == 1;
                    char la = spec.charAt(0);
                    switch (la) {
                        case '%':
                            // %%
                            result.append('%');
                            break;
                        case 'c':
                            int ordinal = getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, vaArgIdx, LLVMType.int_t);
                            if (ordinal < 0 || ordinal > 0x110000) {
                                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, "character argument not in range(0x110000)");
                            }
                            result.append((char) ordinal);
                            break;
                        case 'd':
                        case 'i':
                            // %d, %i, %ld, %li, %lld, %lli, %zd, %zi
                            if (len != null) {
                                LLVMType llvmType = null;
                                switch (len) {
                                    case "ll":
                                        llvmType = LLVMType.longlong_t;
                                        break;
                                    case "l":
                                        llvmType = LLVMType.long_t;
                                        break;
                                    case "z":
                                        llvmType = LLVMType.Py_ssize_t;
                                        break;
                                }
                                if (llvmType != null) {
                                    Object value = getVaArgsNode.execute(vaList, vaArgIdx, llvmType);
                                    vaArgIdx++;
                                    result.append(castToLong(interopLibrary, raiseNode, value));
                                    valid = true;
                                }
                            } else {
                                result.append(getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, vaArgIdx, LLVMType.int_t));
                                vaArgIdx++;
                                valid = true;
                            }
                            break;
                        case 'u':
                            // %u, %lu, %llu, %zu
                            if (len != null) {
                                LLVMType llvmType = null;
                                switch (len) {
                                    case "ll":
                                        llvmType = LLVMType.ulonglong_t;
                                        break;
                                    case "l":
                                        llvmType = LLVMType.ulong_t;
                                        break;
                                    case "z":
                                        llvmType = LLVMType.size_t;
                                        break;
                                }
                                if (llvmType != null) {
                                    Object value = getVaArgsNode.execute(vaList, vaArgIdx, llvmType);
                                    vaArgIdx++;
                                    result.append(castToLong(interopLibrary, raiseNode, value));
                                    valid = true;
                                }
                            } else {
                                result.append(Integer.toUnsignedString(getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, vaArgIdx, LLVMType.uint_t)));
                                vaArgIdx++;
                                valid = true;
                            }
                            break;
                        case 'x':
                            // %x
                            result.append(Integer.toHexString(getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, vaArgIdx, LLVMType.int_t)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 's':
                            // %s
                            Object unicodeObj = fromCharPointerNode.execute(getVaArgsNode.getCharPtr(vaList, vaArgIdx));
                            String sValue = castToJavaStringNode.execute(unicodeObj);
                            try {
                                if (prec == -1) {
                                    result.append(sValue);
                                } else {
                                    result.append(sValue, 0, Math.min(sValue.length(), prec));
                                }
                            } catch (CannotCastException e) {
                                // That should really not happen because we created the unicode
                                // object with FromCharPointerNode which guarantees to return a
                                // String/PString.
                                throw CompilerDirectives.shouldNotReachHere();
                            }
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'p':
                            // %p
                            result.append("0x").append(Long.toHexString(getPyObject(getVaArgsNode, vaList, vaArgIdx).hashCode()));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'A':
                            // %A
                            result.append(callBuiltin(contextRef.get(), BuiltinNames.ASCII, getPyObject(getVaArgsNode, vaList, vaArgIdx)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'U':
                            // %U
                            result.append(castToJavaStringNode.execute(getPyObject(getVaArgsNode, vaList, vaArgIdx)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'V':
                            // %V
                            Object pyObjectPtr = getVaArgsNode.getPyObjectPtr(vaList, vaArgIdx);
                            if (InteropLibrary.getUncached().isNull(pyObjectPtr)) {
                                unicodeObj = fromCharPointerNode.execute(getVaArgsNode.getCharPtr(vaList, vaArgIdx + 1));
                            } else {
                                unicodeObj = toJavaNode.execute(pyObjectPtr);
                            }
                            result.append(castToJavaStringNode.execute(unicodeObj));
                            vaArgIdx += 2;
                            valid = true;
                            break;
                        case 'S':
                            // %S
                            result.append(callBuiltin(contextRef.get(), BuiltinNames.STR, getPyObject(getVaArgsNode, vaList, vaArgIdx)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'R':
                            // %R
                            result.append(callBuiltin(contextRef.get(), BuiltinNames.REPR, getPyObject(getVaArgsNode, vaList, vaArgIdx)));
                            vaArgIdx++;
                            valid = true;
                            break;
                    }
                    // this means, we did not detect a valid format specifier, so add the whole
                    // group
                    if (!valid) {
                        result.append(matcher.group());
                    }
                }
                // add anything after the last matched group (or the whole format string if nothing
                // matched)
                result.append(format, cur, format.length());
            } catch (InteropException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Error when accessing variable argument at position %d", vaArgIdx);
            } finally {
                current.get();
            }
            return result.toString();
        }

        private static int getPrec(String prec) {
            if (prec == null) {
                return -1;
            }
            return Integer.parseInt(prec);
        }

        /**
         * Read an element from the {@code va_list} with the specified type and cast it to a Java
         * {@code int}. Throws a {@code SystemError} if this is not possible.
         */
        private static int getAndCastToInt(GetVaArgsNode getVaArgsNode, InteropLibrary lib, PRaiseNode raiseNode, Object vaList, int idx, LLVMType llvmType) throws InteropException {
            Object value = getVaArgsNode.execute(vaList, idx, llvmType);
            if (lib.fitsInInt(value)) {
                try {
                    return lib.asInt(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, "%p object cannot be interpreted as integer", value);
        }

        /**
         * Cast a value to a Java {@code long}. Throws a {@code SystemError} if this is not
         * possible.
         */
        private static long castToLong(InteropLibrary lib, PRaiseNode raiseNode, Object value) {
            if (lib.fitsInLong(value)) {
                try {
                    return lib.asLong(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, "%p object cannot be interpreted as integer", value);
        }

        private static Object getPyObject(GetVaArgsNode getVaArgsNode, Object vaList, int idx) throws InteropException {
            return ToJavaNodeGen.getUncached().execute(getVaArgsNode.getPyObjectPtr(vaList, idx));
        }

        @TruffleBoundary
        private static Object callBuiltin(PythonContext context, String builtinName, Object object) {
            Object attribute = PythonObjectLibrary.getUncached().lookupAttribute(context.getBuiltins(), null, builtinName);
            return CastToJavaStringNodeGen.getUncached().execute(PythonObjectLibrary.getUncached().callObject(attribute, null, object));
        }
    }

    /**
     * Attaches the appropriate LLVM type to the provided pointer object making the pointer to be
     * typed (i.e. {@code interopLib.hasMetaObject(ptr) == true}) and thus allows to do direct
     * member access via interop.<br/>
     */
    @GenerateUncached
    public abstract static class AttachLLVMTypeNode extends Node {

        public abstract TruffleObject execute(TruffleObject ptr);

        @Specialization(guards = "lib.hasMetaObject(ptr)", limit = "1")
        static TruffleObject doTyped(TruffleObject ptr,
                        @CachedLibrary("ptr") @SuppressWarnings("unused") InteropLibrary lib) {
            return ptr;
        }

        @Specialization(guards = "!lib.hasMetaObject(ptr)", limit = "1", replaces = "doTyped")
        static TruffleObject doUntyped(TruffleObject ptr,
                        @CachedLibrary("ptr") @SuppressWarnings("unused") InteropLibrary lib,
                        @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode,
                        @Shared("callGetObTypeNode") @Cached PCallCapiFunction callGetObTypeNode,
                        @Shared("callPolyglotFromTypedNode") @Cached PCallCapiFunction callPolyglotFromTypedNode,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode) {
            Object type = asPythonObjectNode.execute(callGetObTypeNode.call(FUN_GET_OB_TYPE, ptr));
            Object llvmType = getSulongTypeNode.execute(type);
            return (TruffleObject) callPolyglotFromTypedNode.call(FUN_POLYGLOT_FROM_TYPED, ptr, llvmType);
        }

        @Specialization(limit = "1", replaces = {"doTyped", "doUntyped"})
        static TruffleObject doGeneric(TruffleObject ptr,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode,
                        @Shared("callGetObTypeNode") @Cached PCallCapiFunction callGetObTypeNode,
                        @Shared("callPolyglotFromTypedNode") @Cached PCallCapiFunction callPolyglotFromTypedNode,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode) {
            if (!lib.hasMetaObject(ptr)) {
                return doUntyped(ptr, lib, getSulongTypeNode, callGetObTypeNode, callPolyglotFromTypedNode, asPythonObjectNode);
            }
            return ptr;
        }
    }

}
