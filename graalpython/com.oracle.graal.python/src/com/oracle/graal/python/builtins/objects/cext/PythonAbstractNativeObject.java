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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_GENERIC_GET_DICT;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.ProfileClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.ProfileClassNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(PythonObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class PythonAbstractNativeObject extends PythonAbstractObject implements PythonNativeObject, PythonNativeClass {

    public final TruffleObject object;

    public PythonAbstractNativeObject(TruffleObject object) {
        this.object = object;
    }

    public int compareTo(Object o) {
        return 0;
    }

    public void lookupChanged() {
        // TODO invalidate cached native MRO
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("not yet implemented");
    }

    public TruffleObject getPtr() {
        return object;
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        // this is important for the default '__hash__' implementation
        return Objects.hashCode(object);
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PythonAbstractNativeObject other = (PythonAbstractNativeObject) obj;
        return Objects.equals(object, other.object);
    }

    public boolean equalsProfiled(Object obj, ValueProfile profile) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PythonAbstractNativeObject other = (PythonAbstractNativeObject) obj;
        return Objects.equals(profile.profile(object), profile.profile(other.object));
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PythonAbstractNativeObject(%s)", object);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasDict() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void setDict(PDict value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void deleteDict() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public Object asIndexWithState(ThreadState threadState,
                    @CachedLibrary("this") PythonObjectLibrary plib,
                    @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                    // arguments for super-implementation call
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @CachedLibrary(limit = "5") PythonObjectLibrary resultLib,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached ConditionProfile noIndex,
                    @Exclusive @Cached ConditionProfile resultProfile,
                    @Exclusive @Cached ConditionProfile gotState,
                    @Cached IsBuiltinClassProfile isInt,
                    @Cached WarnNode warnNode) {
        if (isSubtypeNode.execute(plib.getLazyPythonClass(this), PythonBuiltinClassType.PInt)) {
            if (!isInt.profileObject(this, PythonBuiltinClassType.PInt)) {
                VirtualFrame frame = null;
                if (gotState.profile(threadState != null)) {
                    frame = PArguments.frameForCall(threadState);
                }
                warnNode.warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                                ErrorMessages.P_RETURNED_NON_P,
                                this, "__index__", "int", this, "int");
            }
            return this; // subclasses of 'int' should do early return
        } else {
            return asIndexWithState(threadState, plib, methodLib, resultLib, raise, isSubtypeNode, noIndex, resultProfile, gotState, isInt, warnNode);
        }
    }

    @ExportMessage
    public PDict getDict(
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached ToSulongNode toSulong,
                    @Exclusive @Cached ToJavaNode toJava,
                    @Exclusive @Cached PCallCapiFunction callGetDictNode) {
        Object javaDict = toJava.execute(callGetDictNode.call(FUN_PY_OBJECT_GENERIC_GET_DICT, toSulong.execute(this)));
        if (javaDict instanceof PDict) {
            return (PDict) javaDict;
        } else if (javaDict == PNone.NO_VALUE) {
            return null;
        } else {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, javaDict);
        }
    }

    @ExportMessage
    @GenerateUncached
    public abstract static class GetLazyPythonClass {
        static Assumption getSingleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        @Specialization(guards = "object == cachedObject", limit = "1", assumptions = "getSingleContextAssumption()")
        @SuppressWarnings("unused")
        static Object getNativeClassCachedIdentity(PythonAbstractNativeObject object,
                        @Exclusive @Cached(value = "object", weak = true) PythonAbstractNativeObject cachedObject,
                        @Exclusive @Cached("getNativeClassUncached(object)") Object cachedClass) {
            // TODO: (tfel) is this really something we can do? It's so rare for this class to
            // change that it shouldn't be worth the effort, but in native code, anything can
            // happen. OTOH, CPython also has caches that can become invalid when someone just
            // goes and changes the ob_type of an object.
            return cachedClass;
        }

        @Specialization(guards = "isSame(lib, cachedObject, object)", assumptions = "getSingleContextAssumption()")
        @SuppressWarnings("unused")
        static Object getNativeClassCached(PythonAbstractNativeObject object,
                        @Exclusive @Cached(value = "object", weak = true) PythonAbstractNativeObject cachedObject,
                        @Exclusive @Cached("getNativeClassUncached(object)") Object cachedClass,
                        @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib) {
            // TODO same as for 'getNativeClassCachedIdentity'
            return cachedClass;
        }

        @Specialization(guards = {"lib.hasMembers(object.getPtr())"}, //
                        replaces = {"getNativeClassCached", "getNativeClassCachedIdentity"}, //
                        limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getNativeClassByMember(PythonAbstractNativeObject object,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @Exclusive @Cached ProfileClassNode classProfile) throws UnknownIdentifierException, UnsupportedMessageException {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return classProfile.profile(toJavaNode.execute(lib.readMember(object.getPtr(), NativeMember.OB_TYPE.getMemberName())));
        }

        @Specialization(guards = {"!lib.hasMembers(object.getPtr())"}, //
                        replaces = {"getNativeClassCached", "getNativeClassCachedIdentity", "getNativeClassByMember"}, //
                        limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getNativeClassByMemberAttachType(PythonAbstractNativeObject object,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callGetObTypeNode,
                        @Exclusive @Cached CExtNodes.GetLLVMType getLLVMType,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @Exclusive @Cached ProfileClassNode classProfile) throws UnknownIdentifierException, UnsupportedMessageException {
            Object typedPtr = callGetObTypeNode.call(NativeCAPISymbol.FUN_POLYGLOT_FROM_TYPED, object.getPtr(), getLLVMType.execute(CApiContext.LLVMType.PyObject));
            return classProfile.profile(toJavaNode.execute(lib.readMember(typedPtr, NativeMember.OB_TYPE.getMemberName())));
        }

        @Specialization(replaces = {"getNativeClassCached", "getNativeClassCachedIdentity", "getNativeClassByMember", "getNativeClassByMemberAttachType"})
        static Object getNativeClass(PythonAbstractNativeObject object,
                        @Exclusive @Cached PCallCapiFunction callGetObTypeNode,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode,
                        @Exclusive @Cached ProfileClassNode classProfile) {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return classProfile.profile(toJavaNode.execute(callGetObTypeNode.call(FUN_GET_OB_TYPE, object.getPtr())));
        }

        static boolean isSame(InteropLibrary lib, PythonAbstractNativeObject cachedObject, PythonAbstractNativeObject object) {
            return lib.isIdentical(cachedObject.object, object.object, lib);
        }

        public static Object getNativeClassUncached(PythonAbstractNativeObject object) {
            // do not wrap 'object.object' since that is really the native pointer object
            return getNativeClass(object, PCallCapiFunction.getUncached(), AsPythonObjectNodeGen.getUncached(), ProfileClassNodeGen.getUncached());
        }

    }

    @ExportMessage
    int identityHashCode(@CachedLibrary("this.object") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.identityHashCode(object);
    }

    @ExportMessage
    boolean isIdentical(Object other, InteropLibrary otherInterop,
                    @Cached("createClassProfile()") ValueProfile otherProfile,
                    @CachedLibrary(limit = "1") InteropLibrary thisLib,
                    @CachedLibrary("this.object") InteropLibrary objLib,
                    @CachedLibrary(limit = "1") InteropLibrary otherObjLib,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object profiled = otherProfile.profile(other);
            if (profiled instanceof PythonAbstractNativeObject) {
                return objLib.isIdentical(object, ((PythonAbstractNativeObject) profiled).object, otherObjLib);
            }
            return otherInterop.isIdentical(profiled, this, thisLib);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doPythonAbstractNativeObject(PythonAbstractNativeObject receiver, PythonAbstractNativeObject other,
                        @CachedLibrary("receiver.object") InteropLibrary objLib,
                        @CachedLibrary(limit = "1") InteropLibrary otherObjectLib) {
            return TriState.valueOf(objLib.isIdentical(receiver.object, other.object, otherObjectLib));
        }

        @Fallback
        static TriState doOther(PythonAbstractNativeObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage(library = PythonObjectLibrary.class, name = "isLazyPythonClass")
    @ExportMessage(library = InteropLibrary.class)
    boolean isMetaObject(
                    @Exclusive @Cached TypeNodes.IsTypeNode isType,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isType.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Cached PForeignToPTypeNode convert,
                    @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!isType.execute(this)) {
                throw UnsupportedMessageException.create();
            }
            return isSubtype.execute(plib.getLazyPythonClass(convert.executeConvert(instance)), this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    String getMetaSimpleName(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached GetTypeMemberNode getTpNameNode,
                    @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        return getSimpleName(getMetaQualifiedName(isType, getTpNameNode, castToJavaStringNode, gil));
    }

    @TruffleBoundary
    private static String getSimpleName(String fqname) {
        int firstDot = fqname.indexOf('.');
        if (firstDot != -1) {
            return fqname.substring(firstDot + 1);
        }
        return fqname;
    }

    @ExportMessage
    String getMetaQualifiedName(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached GetTypeMemberNode getTpNameNode,
                    @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!isType.execute(this)) {
                throw UnsupportedMessageException.create();
            }
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            try {
                return castToJavaStringNode.execute(getTpNameNode.execute(this, NativeMember.TP_NAME));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } finally {
            gil.release(mustRelease);
        }
    }
}
