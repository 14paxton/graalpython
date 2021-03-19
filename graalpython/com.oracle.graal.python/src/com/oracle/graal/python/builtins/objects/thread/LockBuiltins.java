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
package com.oracle.graal.python.builtins.objects.thread;

import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.DEFAULT_BLOCKING;
import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.TIMEOUT_MAX;
import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.UNSET_TIMEOUT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltinsFactory.AcquireLockNodeFactory;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PLock, PythonBuiltinClassType.PRLock})
public class LockBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LockBuiltinsFactory.getFactories();
    }

    @Builtin(name = "acquire", minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class AcquireLockNode extends PythonTernaryBuiltinNode {
        private @Child CoerceToBooleanNode castToBooleanNode;
        private @Child PythonObjectLibrary pythonObjectLibrary;
        private @CompilationFinal ConditionProfile isBlockingProfile = ConditionProfile.createBinaryProfile();
        private @CompilationFinal ConditionProfile defaultTimeoutProfile = ConditionProfile.createBinaryProfile();

        private CoerceToBooleanNode getCastToBooleanNode() {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CoerceToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode;
        }

        private PythonObjectLibrary getPythonObjectLibrary() {
            if (pythonObjectLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pythonObjectLibrary = insert(PythonObjectLibrary.getFactory().createDispatched(1));
            }
            return pythonObjectLibrary;
        }

        @TruffleBoundary
        private static boolean acquireBlocking(AbstractPythonLock self) {
            return self.acquireBlocking();
        }

        @Specialization
        boolean doAcquire(VirtualFrame frame, AbstractPythonLock self, Object blocking, Object timeout,
                        @Cached GilNode gil) {
            // args setup
            boolean isBlocking = (blocking instanceof PNone) ? DEFAULT_BLOCKING : getCastToBooleanNode().executeBoolean(frame, blocking);
            double timeoutSeconds = UNSET_TIMEOUT;
            if (!(timeout instanceof PNone)) {
                timeoutSeconds = getPythonObjectLibrary().asJavaDouble(timeout);

                if (timeoutSeconds != UNSET_TIMEOUT) {
                    if (!isBlocking) {
                        throw raise(ValueError, ErrorMessages.CANT_SPECIFY_TIMEOUT_FOR_NONBLOCKING);
                    }

                    if (timeoutSeconds < 0) {
                        throw raise(ValueError, ErrorMessages.TIMEOUT_VALUE_MUST_BE_POSITIVE);
                    } else if (timeoutSeconds > TIMEOUT_MAX) {
                        throw raise(OverflowError, ErrorMessages.TIMEOUT_VALUE_TOO_LARGE);
                    }
                }
            }

            // acquire lock
            if (isBlockingProfile.profile(!isBlocking)) {
                return self.acquireNonBlocking();
            } else {
                gil.release(true);
                try {
                    if (defaultTimeoutProfile.profile(timeoutSeconds == UNSET_TIMEOUT)) {
                        return acquireBlocking(self);
                    } else {
                        return self.acquireTimeout(timeoutSeconds);
                    }
                } finally {
                    gil.acquire();
                }
            }
        }

        public static AcquireLockNode create() {
            return AcquireLockNodeFactory.create();
        }
    }

    @Builtin(name = "acquire_lock", minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class AcquireLockLockNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object acquire(VirtualFrame frame, PLock self, Object blocking, Object timeout,
                        @Cached("create()") AcquireLockNode acquireLockNode) {
            return acquireLockNode.call(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class EnterLockNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object acquire(VirtualFrame frame, AbstractPythonLock self, Object blocking, Object timeout,
                        @Cached("create()") AcquireLockNode acquireLockNode) {
            return acquireLockNode.call(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReleaseLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doRelease(PLock self) {
            self.release();
            return PNone.NONE;
        }

        @Specialization
        Object doRelease(PRLock self) {
            if (!self.isOwned()) {
                throw raise(PythonBuiltinClassType.RuntimeError, "lock not held");
            }
            self.release();
            return PNone.NONE;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitLockNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object exit(AbstractPythonLock self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object traceback) {
            self.release();
            return PNone.NONE;
        }
    }

    @Builtin(name = "locked", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsLockedLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isLocked(PLock self) {
            return self.locked();
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprLockNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        @TruffleBoundary
        String repr(PLock self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return String.format("<%s %s object at %s>",
                            (self.locked()) ? "locked" : "unlocked",
                            GetNameNode.doSlowPath(lib.getLazyPythonClass(self)),
                            self.hashCode());
        }

        @Specialization(limit = "2")
        @TruffleBoundary
        String repr(PRLock self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return String.format("<%s %s object owner=%d count=%d at %s>",
                            (self.locked()) ? "locked" : "unlocked",
                            GetNameNode.doSlowPath(lib.getLazyPythonClass(self)),
                            self.getOwnerId(),
                            self.getCount(),
                            self.hashCode());
        }
    }
}
