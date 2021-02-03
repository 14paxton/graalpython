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
package com.oracle.graal.python.builtins.objects.object;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = Long.class)
final class DefaultPythonLongExports {
    @ExportMessage
    static boolean isHashable(@SuppressWarnings("unused") Long value) {
        return true;
    }

    @ExportMessage
    static boolean canBeIndex(@SuppressWarnings("unused") Long value) {
        return true;
    }

    @ExportMessage
    static Object asIndexWithState(Long value, @SuppressWarnings("unused") ThreadState state) {
        return value;
    }

    @ExportMessage
    static class AsSizeWithState {
        @Specialization(rewriteOn = OverflowException.class)
        static int noOverflow(Long self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") ThreadState state) throws OverflowException {
            return PInt.intValueExact(self);
        }

        @Specialization(replaces = "noOverflow")
        static int withOverflow(Long self, Object type, @SuppressWarnings("unused") ThreadState state,
                        @Exclusive @Cached PRaiseNode raise) {
            try {
                return PInt.intValueExact(self);
            } catch (OverflowException e) {
                if (type != null) {
                    throw raise.raiseNumberTooLarge(type, self);
                } else {
                    return self > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                }
            }
        }
    }

    @ExportMessage
    static Object getLazyPythonClass(@SuppressWarnings("unused") Long value) {
        return PythonBuiltinClassType.PInt;
    }

    @ExportMessage
    static long hashWithState(Long value, @SuppressWarnings("unused") ThreadState state) {
        return hash(value);
    }

    @Ignore
    static long hash(long value) {
        long h = value % SysModuleBuiltins.HASH_MODULUS;
        return h == -1 ? -2 : h;
    }

    @ExportMessage
    static boolean isTrueWithState(Long value, @SuppressWarnings("unused") ThreadState threadState) {
        return value != 0;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean li(Long receiver, int other) {
            return receiver == other;
        }

        @Specialization
        static boolean ll(Long receiver, long other) {
            return receiver == other;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean lP(Long receiver, PInt other,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) throws OverflowException {
            if (isBuiltin.profileObject(other, PythonBuiltinClassType.PInt)) {
                return receiver == other.longValueExact();
            }
            return false;
        }

        @Specialization(replaces = "lP")
        static boolean lPOverflow(Long receiver, PInt other,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            if (isBuiltin.profileObject(other, PythonBuiltinClassType.PInt)) {
                if (other.fitsInLong()) {
                    return receiver == other.longValue();
                }
            }
            return false;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean lO(Long receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    static class EqualsInternal {
        @Specialization
        static int lb(Long receiver, boolean other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver == 1 && other || receiver == 0 && !other) ? 1 : 0;
        }

        @Specialization
        static int li(Long receiver, int other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int ll(Long receiver, long other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int lI(Long receiver, PInt other, @SuppressWarnings("unused") ThreadState threadState) {
            return other.compareTo((long) receiver) == 0 ? 1 : 0;
        }

        @Specialization
        static int ld(Long receiver, double other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int lF(Long receiver, PFloat other, @SuppressWarnings("unused") ThreadState threadState,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            // n.b.: long objects cannot compare here, but if its a builtin float we can shortcut
            if (isBuiltin.profileIsAnyBuiltinClass(lib.getLazyPythonClass(other))) {
                return receiver == other.getValue() ? 1 : 0;
            } else {
                return -1;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static int lO(Long receiver, Object other, @SuppressWarnings("unused") ThreadState threadState) {
            return -1;
        }
    }

    @ImportStatic(PythonOptions.class)
    @ExportMessage
    @SuppressWarnings("unused")
    static class EqualsWithState {
        @Specialization
        static boolean lb(Long receiver, boolean other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == 1 && other || receiver == 0 && !other;
        }

        @Specialization
        static boolean li(Long receiver, int other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean ll(Long receiver, long other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean lI(Long receiver, PInt other, PythonObjectLibrary oLib, ThreadState threadState) {
            return other.compareTo((long) receiver) == 0;
        }

        @Specialization
        static boolean ld(Long receiver, double other, PythonObjectLibrary oLib, ThreadState threadState) {
            return receiver == other;
        }

        @Specialization
        static boolean lF(Long receiver, PFloat other, PythonObjectLibrary oLib, ThreadState threadState,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            // n.b.: long objects cannot compare here, but if its a builtin float we can shortcut
            if (isBuiltin.profileIsAnyBuiltinClass(oLib.getLazyPythonClass(other))) {
                return receiver == other.getValue();
            } else {
                return oLib.equalsInternal(other, receiver, threadState) == 1;
            }
        }

        @Specialization(replaces = {"lb", "li", "ll", "lI", "ld", "lF"})
        static boolean lO(Long receiver, Object other, PythonObjectLibrary oLib, ThreadState threadState) {
            if (other instanceof Boolean) {
                return lb(receiver, (boolean) other, oLib, threadState);
            } else if (other instanceof Integer) {
                return li(receiver, (int) other, oLib, threadState);
            } else if (other instanceof Long) {
                return ll(receiver, (long) other, oLib, threadState);
            } else if (other instanceof PInt) {
                return lI(receiver, (PInt) other, oLib, threadState);
            } else if (other instanceof Double) {
                return ld(receiver, (double) other, oLib, threadState);
            } else {
                return oLib.equalsInternal(other, receiver, threadState) == 1;
            }
        }
    }

    @ExportMessage
    @TruffleBoundary
    static String asPStringWithState(Long x, @SuppressWarnings("unused") ThreadState state) {
        return Long.toString(x);
    }

    @ExportMessage
    static int asFileDescriptorWithState(Long x, @SuppressWarnings("unused") ThreadState state,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached CastToJavaIntExactNode castToJavaIntNode,
                    @Exclusive @Cached IsBuiltinClassProfile errorProfile) {
        try {
            return PInt.asFileDescriptor(castToJavaIntNode.execute(x), raiseNode);
        } catch (PException e) {
            e.expect(PythonBuiltinClassType.TypeError, errorProfile);
            // we need to convert the TypeError to an OverflowError
            throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    static boolean canBeJavaDouble(@SuppressWarnings("unused") Long receiver) {
        return true;
    }

    @ExportMessage
    static double asJavaDoubleWithState(Long receiver, @SuppressWarnings("unused") ThreadState state) {
        return receiver.doubleValue();
    }

    @ExportMessage
    static boolean canBeJavaLong(@SuppressWarnings("unused") Long receiver) {
        return true;
    }

    @ExportMessage
    static long asJavaLongWithState(Long receiver, @SuppressWarnings("unused") ThreadState state) {
        return receiver;
    }

    @ExportMessage
    static boolean canBePInt(@SuppressWarnings("unused") Long receiver) {
        return true;
    }

    @ExportMessage
    static long asPIntWithState(Long receiver, @SuppressWarnings("unused") ThreadState state) {
        return receiver;
    }

    @ExportMessage
    public static Object lookupAttributeInternal(Long receiver, ThreadState state, String name, boolean strict,
                    @Cached ConditionProfile gotState,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeNode lookup) {
        VirtualFrame frame = null;
        if (gotState.profile(state != null)) {
            frame = PArguments.frameForCall(state);
        }
        return lookup.execute(frame, receiver, name, strict);
    }

    @ExportMessage
    static Object lookupAttributeOnTypeInternal(@SuppressWarnings("unused") Long receiver, String name, boolean strict,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeOnTypeNode lookup) {
        return lookup.execute(PythonBuiltinClassType.PInt, name, strict);
    }

    @ExportMessage
    static Object lookupAndCallSpecialMethodWithState(Long receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeOnTypeStrict(receiver, methodName);
        return methodLib.callUnboundMethodWithState(method, state, receiver, arguments);
    }

    @ExportMessage
    static Object lookupAndCallRegularMethodWithState(Long receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeStrictWithState(receiver, state, methodName);
        return methodLib.callObjectWithState(method, state, arguments);
    }

    @ExportMessage
    static boolean typeCheck(@SuppressWarnings("unused") Long receiver, Object type,
                    @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached IsSubtypeNode isSubtypeNode) {
        Object instanceClass = PythonBuiltinClassType.PInt;
        return isSameTypeNode.execute(instanceClass, type) || isSubtypeNode.execute(instanceClass, type);
    }
}
