/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.deque;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDequeIter, PythonBuiltinClassType.PDequeRevIter})
public class DequeIterBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DequeIterBuiltinsFactory.getFactories();
    }

    // _deque_iterator.__iter__()
    @Builtin(name = SpecialMethodNames.__ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeIterIterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDequeIter doGeneric(PDequeIter self) {
            return self;
        }
    }

    // _deque_iterator.__next__()
    @Builtin(name = SpecialMethodNames.__NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeIterNextNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doGeneric(PDequeIter self) {
            try {
                if (self.startState == self.deque.getState()) {
                    if (!self.hasNext()) {
                        assert self.lengthHint() == 0;
                        throw raise(StopIteration);
                    }
                    return self.next();
                }
            } catch (NoSuchElementException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (ConcurrentModificationException e) {
                // fall through
            }
            self.reset();
            throw PRaiseNode.raiseUncached(this, RuntimeError, ErrorMessages.DEQUE_MUTATED_DURING_ITERATION);
        }
    }

    // deque.__length_hint__()
    @Builtin(name = SpecialMethodNames.__LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeIterLengthHintNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int doGeneric(PDequeIter self) {
            return self.lengthHint();
        }
    }
}
