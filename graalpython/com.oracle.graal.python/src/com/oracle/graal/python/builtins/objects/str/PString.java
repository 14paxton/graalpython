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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.builtins.objects.str.StringNodesFactory.StringMaterializeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonObjectLibrary.class)
public final class PString extends PSequence {
    public static final HiddenKey INTERNED = new HiddenKey("_interned");

    private CharSequence value;

    public PString(Object clazz, Shape instanceShape, CharSequence value) {
        super(clazz, instanceShape);
        this.value = value;
    }

    public String getValue() {
        return StringMaterializeNodeGen.getUncached().execute(this);
    }

    public CharSequence getCharSequence() {
        return value;
    }

    void setCharSequence(String materialized) {
        this.value = materialized;
    }

    @ExportMessage
    static class LengthWithState {

        static boolean isSeqString(CharSequence seq) {
            return seq instanceof String;
        }

        static boolean isLazyString(CharSequence seq) {
            return seq instanceof LazyString;
        }

        static boolean isNativeString(CharSequence seq) {
            return seq instanceof NativeCharSequence;
        }

        static boolean isMaterialized(CharSequence seq) {
            return ((NativeCharSequence) seq).isMaterialized();
        }

        static boolean isBuiltin(PString self, IsBuiltinClassProfile p) {
            return p.profileIsAnyBuiltinObject(self);
        }

        static boolean hasBuiltinLen(PString self, LookupInheritedAttributeNode.Dynamic lookupSelf, LookupAttributeInMRONode.Dynamic lookupString) {
            return lookupSelf.execute(self, __LEN__) == lookupString.execute(PythonBuiltinClassType.PString, __LEN__);
        }

        @Specialization(guards = {
                        "isSeqString(self.getCharSequence())",
                        "isBuiltin(self, profile) || hasBuiltinLen(self, lookupSelf, lookupString)"
        }, limit = "1")
        static int string(PString self, @SuppressWarnings("unused") ThreadState state,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @SuppressWarnings("unused") @Shared("lookupSelf") @Cached LookupInheritedAttributeNode.Dynamic lookupSelf,
                        @SuppressWarnings("unused") @Shared("lookupString") @Cached LookupAttributeInMRONode.Dynamic lookupString) {
            return CompilerDirectives.castExact(self.value, String.class).length();
        }

        @Specialization(guards = {
                        "isLazyString(self.getCharSequence())",
                        "isBuiltin(self, profile) || hasBuiltinLen(self, lookupSelf, lookupString)"
        }, limit = "1")
        static int lazyString(PString self, @SuppressWarnings("unused") ThreadState state,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @SuppressWarnings("unused") @Shared("lookupSelf") @Cached LookupInheritedAttributeNode.Dynamic lookupSelf,
                        @SuppressWarnings("unused") @Shared("lookupString") @Cached LookupAttributeInMRONode.Dynamic lookupString) {
            return CompilerDirectives.castExact(self.value, LazyString.class).length();
        }

        @Specialization(guards = {
                        "isNativeString(self.getCharSequence())", "isMaterialized(self.getCharSequence())",
                        "isBuiltin(self, profile) || hasBuiltinLen(self, lookupSelf, lookupString)"
        }, limit = "1")
        static int nativeString(PString self, @SuppressWarnings("unused") ThreadState state,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @SuppressWarnings("unused") @Shared("lookupSelf") @Cached LookupInheritedAttributeNode.Dynamic lookupSelf,
                        @SuppressWarnings("unused") @Shared("lookupString") @Cached LookupAttributeInMRONode.Dynamic lookupString) {
            return CompilerDirectives.castExact(self.value, NativeCharSequence.class).getMaterialized().length();
        }

        @Specialization(guards = {
                        "isNativeString(self.getCharSequence())", "!isMaterialized(self.getCharSequence())",
                        "isBuiltin(self, profile) || hasBuiltinLen(self, lookupSelf, lookupString)"
        }, replaces = "nativeString", limit = "1")
        static int nativeStringMat(PString self, @SuppressWarnings("unused") ThreadState state,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @SuppressWarnings("unused") @Shared("lookupSelf") @Cached LookupInheritedAttributeNode.Dynamic lookupSelf,
                        @SuppressWarnings("unused") @Shared("lookupString") @Cached LookupAttributeInMRONode.Dynamic lookupString,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Shared("stringMaterializeNode") @Cached StringMaterializeNode materializeNode) {
            // this cast is guaranteed by cast 'isNativeString(self.getCharSequence())'
            NativeCharSequence value = (NativeCharSequence) self.value;
            if (lib.hasArrayElements(value.getPtr())) {
                return value.length(lib, castToJavaIntNode);
            } else {
                return materializeNode.execute(self).length();
            }
        }

        @Specialization(replaces = {"string", "lazyString", "nativeString", "nativeStringMat"})
        static int subclassedString(PString self, ThreadState state,
                        @CachedLibrary("self") PythonObjectLibrary plib,
                        @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                        @Exclusive @Cached ConditionProfile hasLen,
                        @Exclusive @Cached ConditionProfile ltZero,
                        @Shared("raise") @Cached PRaiseNode raiseNode,
                        @Exclusive @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Exclusive @Cached CastToJavaLongLossyNode toLong,
                        @Exclusive @Cached ConditionProfile ignoreOverflow,
                        @Exclusive @Cached BranchProfile overflow) {
            // call the generic implementation in the superclass
            return self.lengthWithState(state, plib, methodLib, hasLen, ltZero, raiseNode, lib, toLong, ignoreOverflow, overflow);
        }
    }

    @ExportMessage
    String asPathWithState(@SuppressWarnings("unused") ThreadState state,
                    @Cached CastToJavaStringNode castToJavaStringNode) {
        try {
            return castToJavaStringNode.execute(this);
        } catch (CannotCastException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not be reached");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        if (value instanceof LazyString) {
            return value.toString().hashCode();
        }
        return value.hashCode();
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.equals(value);
    }

    public boolean isNative() {
        return getNativeWrapper() != null && PythonNativeWrapperLibrary.getUncached().isNative(getNativeWrapper());
    }

    @Override
    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isString() {
        return true;
    }

    @ExportMessage
    String asString(
                    @Cached StringMaterializeNode stringMaterializeNode) {
        return stringMaterializeNode.execute(this);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    static boolean isHashable(@SuppressWarnings("unused") PString self) {
        return true;
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Cached CastToJavaStringNode cast) {
        try {
            return cast.execute(this).codePointAt((int) index);
        } catch (CannotCastException e) {
            throw CompilerDirectives.shouldNotReachHere("A PString should always have an underlying CharSequence");
        }
    }

    @Override
    @ExportMessage
    public long getArraySize(@CachedLibrary("this") PythonObjectLibrary lib) {
        return lib.length(this);
    }

    @ExportMessage.Ignore
    @TruffleBoundary(allowInlining = true)
    public static int length(String s) {
        return s.length();
    }

    @TruffleBoundary(allowInlining = true)
    public static String valueOf(char c) {
        return String.valueOf(c);
    }

    @TruffleBoundary(allowInlining = true)
    public static char charAt(String s, int i) {
        return s.charAt(i);
    }

    @TruffleBoundary(allowInlining = true)
    public static int codePointAt(String s, int i) {
        return s.codePointAt(i);
    }

    @TruffleBoundary(allowInlining = true)
    public static int charCount(int codePoint) {
        return Character.charCount(codePoint);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean isHighSurrogate(char ch) {
        return Character.isHighSurrogate(ch);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean isLowSurrogate(char ch) {
        return Character.isLowSurrogate(ch);
    }

    @TruffleBoundary(allowInlining = true)
    public static int indexOf(String s, String sub, int fromIndex) {
        return s.indexOf(sub, fromIndex);
    }

    @TruffleBoundary(allowInlining = true)
    public static int lastIndexOf(String s, String sub, int fromIndex) {
        return s.lastIndexOf(sub, fromIndex);
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(String str, int start, int end) {
        return str.substring(start, end);
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(String str, int start) {
        return str.substring(start);
    }

    @TruffleBoundary
    public static boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }

    @TruffleBoundary
    public static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint);
    }

    @TruffleBoundary(allowInlining = true)
    public static int codePointCount(String str, int beginIndex, int endIndex) {
        return str.codePointCount(beginIndex, endIndex);
    }

    @Ignore
    @TruffleBoundary
    public static boolean equals(String left, String other) {
        return left.equals(other);
    }

    @TruffleBoundary
    public static String cat(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg);
        }
        return sb.toString();
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean startsWith(String left, String prefix) {
        return left.startsWith(prefix);
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings({"static-method", "unused"})
    public static void setItem(int idx, Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PythonLanguage.getCore().raise(PythonBuiltinClassType.PString, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT);
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isArrayElementModifiable(PString self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isArrayElementInsertable(PString self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isArrayElementRemovable(PString self, long index) {
        return false;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean ss(PString receiver, PString other,
                        @Shared("stringMaterializeNode") @Cached StringMaterializeNode materializeNode,
                        @Cached StringNodes.IsInternedStringNode isInternedStringNode) {
            if (isInternedStringNode.execute(receiver) && isInternedStringNode.execute(other)) {
                return materializeNode.execute(receiver).equals(materializeNode.execute(other));
            }
            return receiver == other;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean sO(PString receiver, Object other) {
            return false;
        }
    }
}
