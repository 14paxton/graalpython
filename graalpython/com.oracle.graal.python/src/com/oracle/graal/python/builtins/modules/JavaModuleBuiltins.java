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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.interop.InteropByteArray;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@CoreFunctions(defineModule = JavaModuleBuiltins.JAVA)
public class JavaModuleBuiltins extends PythonBuiltins {
    protected static final String JAVA = "java";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JavaModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("__path__", "java!");
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule javaModule = core.lookupBuiltinModule(JAVA);
        javaModule.setAttribute(__GETATTR__, javaModule.getAttribute(GetAttrNode.JAVA_GETATTR));
    }

    @Builtin(name = "type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TypeNode extends PythonUnaryBuiltinNode {
        private Object get(String name) {
            Env env = getContext().getEnv();
            if (!env.isHostLookupAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, ErrorMessages.HOST_LOOKUP_NOT_ALLOWED);
            }
            Object hostValue;
            try {
                hostValue = env.lookupHostSymbol(name);
            } catch (RuntimeException e) {
                hostValue = null;
            }
            if (hostValue == null) {
                throw raise(PythonErrorType.KeyError, ErrorMessages.HOST_SYM_NOT_DEFINED, name);
            } else {
                return hostValue;
            }
        }

        @Specialization
        Object type(String name) {
            return get(name);
        }

        @Specialization
        Object type(PString name) {
            return get(name.getValue());
        }

        @Fallback
        Object doError(Object object) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_P, object);
        }
    }

    @Builtin(name = "add_to_classpath", takesVarArgs = true, doc = "Add all arguments to the classpath.")
    @GenerateNodeFactory
    abstract static class AddToClassPathNode extends PythonBuiltinNode {
        @Specialization
        PNone add(Object[] args,
                        @Cached CastToJavaStringNode castToString) {
            Env env = getContext().getEnv();
            if (!env.isHostLookupAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, ErrorMessages.HOST_ACCESS_NOT_ALLOWED);
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                String entry = null;
                try {
                    entry = castToString.execute(arg);
                    // Always allow accessing JAR files in the language home; folders are allowed
                    // implicitly
                    env.addToHostClassPath(getContext().getPublicTruffleFileRelaxed(entry, ".jar"));
                } catch (CannotCastException e) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CLASSPATH_ARG_MUST_BE_STRING, i + 1, arg);
                } catch (SecurityException e) {
                    throw raise(TypeError, ErrorMessages.INVALD_OR_UNREADABLE_CLASSPATH, entry, e);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "is_function", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsFunctionNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean check(Object object) {
            Env env = getContext().getEnv();
            return env.isHostFunction(object);
        }
    }

    @Builtin(name = "is_object", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean check(Object object) {
            Env env = getContext().getEnv();
            return env.isHostObject(object);
        }
    }

    @Builtin(name = "is_symbol", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSymbolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean check(Object object) {
            Env env = getContext().getEnv();
            return env.isHostSymbol(object);
        }
    }

    @Builtin(name = "is_type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsTypeNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isType(Object object) {
            Env env = getContext().getEnv();
            return env.isHostObject(object) && env.asHostObject(object) instanceof Class<?>;
        }
    }

    @Builtin(name = "instanceof", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstanceOfNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"!isForeign1.execute(object)", "isForeign2.execute(klass)"}, limit = "1")
        boolean check(Object object, TruffleObject klass,
                        @SuppressWarnings("unused") @Shared("isForeign1") @Cached IsForeignObjectNode isForeign1,
                        @SuppressWarnings("unused") @Shared("isForeign2") @Cached IsForeignObjectNode isForeign2) {
            Env env = getContext().getEnv();
            try {
                Object hostKlass = env.asHostObject(klass);
                if (hostKlass instanceof Class<?>) {
                    return ((Class<?>) hostKlass).isInstance(object);
                }
            } catch (ClassCastException cce) {
                throw raise(ValueError, ErrorMessages.KLASS_ARG_IS_NOT_HOST_OBJ, klass);
            }
            return false;
        }

        @Specialization(guards = {"isForeign1.execute(object)", "isForeign2.execute(klass)"}, limit = "1")
        boolean checkForeign(Object object, TruffleObject klass,
                        @SuppressWarnings("unused") @Shared("isForeign1") @Cached IsForeignObjectNode isForeign1,
                        @SuppressWarnings("unused") @Shared("isForeign2") @Cached IsForeignObjectNode isForeign2) {
            Env env = getContext().getEnv();
            try {
                Object hostObject = env.asHostObject(object);
                Object hostKlass = env.asHostObject(klass);
                if (hostKlass instanceof Class<?>) {
                    return ((Class<?>) hostKlass).isInstance(hostObject);
                }
            } catch (ClassCastException cce) {
                throw raise(ValueError, ErrorMessages.OBJ_OR_KLASS_ARGS_IS_NOT_HOST_OBJ, object, klass);
            }
            return false;
        }

        @Fallback
        boolean fallback(Object object, Object klass) {
            throw raise(TypeError, ErrorMessages.UNSUPPORTED_INSTANCEOF, object, klass);
        }
    }

    @Builtin(name = GetAttrNode.JAVA_GETATTR, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetAttrNode extends PythonBuiltinNode {

        protected static final String JAVA_GETATTR = "java_getattr";
        private static final String JAVA_PKG_LOADER = "JavaPackageLoader";
        private static final String MAKE_GETATTR = "_make_getattr";

        @CompilationFinal protected Object getAttr;

        private Object getAttr(VirtualFrame frame, PythonModule mod, PythonObjectLibrary lib) {
            if (getAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object javaLoader = PyObjectLookupAttr.getUncached().executeStrict(frame, this, mod, JAVA_PKG_LOADER);
                getAttr = lib.lookupAndCallRegularMethod(javaLoader, frame, MAKE_GETATTR, JAVA);
            }
            return getAttr;
        }

        @Specialization
        Object none(VirtualFrame frame, PythonModule mod, Object name,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return lib.callObject(getAttr(frame, mod, lib), frame, name);
        }
    }

    @Builtin(name = "as_java_byte_array", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsJavaByteArrayNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doBytesByteStorage(PBytesLike object) {
            return new PUnsignedBytesWrapper(object);
        }

        @Specialization(guards = "!isBytes(object)", limit = "3")
        static Object doBuffer(Object object,
                        @CachedLibrary("object") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            Object buffer = acquireLib.acquireReadonly(object);
            try {
                return new InteropByteArray(bufferLib.getCopiedByteArray(object));
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    /**
     * A simple wrapper object that bit-casts an integer in range {@code 0-255} to a Java
     * {@code byte}. This can be used to expose a bytes-like object to Java as {@code byte[]}.
     */
    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    @SuppressWarnings("static-method")
    static final class PUnsignedBytesWrapper implements TruffleObject {
        final PBytesLike delegate;

        PUnsignedBytesWrapper(PBytesLike delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        boolean hasArrayElements(
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib) {
            return delegateLib.hasArrayElements(delegate);
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib) {
            return delegateLib.isArrayElementReadable(delegate, index);
        }

        @ExportMessage
        long getArraySize(
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib) throws UnsupportedMessageException {
            return delegateLib.getArraySize(delegate);
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib,
                        @CachedLibrary(limit = "1") InteropLibrary elementLib,
                        @Cached GilNode gil) throws InvalidArrayIndexException, UnsupportedMessageException {
            boolean mustRelease = gil.acquire();
            try {
                Object element = delegateLib.readArrayElement(delegate, index);
                if (elementLib.fitsInLong(element)) {
                    long i = elementLib.asLong(element);
                    if (compareUnsigned(i, Byte.MAX_VALUE) <= 0) {
                        return (byte) i;
                    } else if (compareUnsigned(i, 0xFF) <= 0) {
                        return (byte) -(-i & 0xFF);
                    }
                }
                throw CompilerDirectives.shouldNotReachHere("bytes object contains non-byte values");
            } finally {
                gil.release(mustRelease);
            }
        }

        /**
         * This is taken from {@link Long#compare(long, long)}} (just to avoid a
         * {@code TruffleBoundary}).
         */
        private static int compare(long x, long y) {
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }

        /**
         * This is taken from {@link Long#compareUnsigned(long, long)}} (just to avoid a
         * {@code TruffleBoundary}).
         */
        private static int compareUnsigned(long x, long y) {
            return compare(x + Long.MIN_VALUE, y + Long.MIN_VALUE);
        }
    }
}
