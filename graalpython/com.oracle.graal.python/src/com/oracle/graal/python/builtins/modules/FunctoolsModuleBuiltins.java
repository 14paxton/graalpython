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

import static com.oracle.graal.python.nodes.ErrorMessages.REDUCE_EMPTY_SEQ;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_N_MUST_SUPPORT_ITERATION;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = "_functools")
public class FunctoolsModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctoolsModuleBuiltinsFactory.getFactories();
    }

    // functools.reduce(function, iterable[, initializer])
    @Builtin(name = "replace", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, doc = "reduce(function, sequence[, initial]) -> value\n" +
                    "\n" +
                    "Apply a function of two arguments cumulatively to the items of a sequence,\n" +
                    "from left to right, so as to reduce the sequence to a single value.\n" +
                    "For example, reduce(lambda x, y: x+y, [1, 2, 3, 4, 5]) calculates\n" +
                    "((((1+2)+3)+4)+5).  If initial is present, it is placed before the items\n" +
                    "of the sequence in the calculation, and serves as a default when the\n" +
                    "sequence is empty.")
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "isNoValue(initial)")
        Object doReduceNoInitial(VirtualFrame frame, Object function, Object sequence, @SuppressWarnings("unused") PNone initial,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinClassProfile stopIterProfile,
                        @Cached IsBuiltinClassProfile typeError) {
            return doReduce(frame, function, sequence, null, getIter, nextNode, callNode, stopIterProfile, typeError);
        }

        @Specialization(guards = "!isNoValue(initial)")
        Object doReduce(VirtualFrame frame, Object function, Object sequence, Object initial,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinClassProfile stopIterProfile,
                        @Cached IsBuiltinClassProfile typeError) {
            Object seqIterator, result = initial;
            try {
                seqIterator = getIter.execute(frame, sequence);
            } catch (PException pe) {
                pe.expectTypeError(typeError);
                throw raise(PythonBuiltinClassType.TypeError, S_ARG_N_MUST_SUPPORT_ITERATION, "reduce()", 2);
            }

            Object[] args = new Object[2];

            while (true) {
                Object op2;
                try {
                    op2 = nextNode.execute(frame, seqIterator);
                    if (result == null) {
                        result = op2;
                    } else {
                        // Update the args tuple in-place
                        args[0] = result;
                        args[1] = op2;
                        result = callNode.execute(frame, function, args);
                    }
                } catch (PException e) {
                    e.expectStopIteration(stopIterProfile);
                    break;
                }
            }

            if (result == null) {
                throw raise(PythonBuiltinClassType.TypeError, REDUCE_EMPTY_SEQ);
            }

            return result;
        }
    }
}
