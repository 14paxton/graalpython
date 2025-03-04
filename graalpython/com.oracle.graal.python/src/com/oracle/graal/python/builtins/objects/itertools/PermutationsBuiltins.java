/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ARGS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PPermutations})
public final class PermutationsBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = PermutationsBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PermutationsBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PPermutations self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization(guards = "self.isStopped()")
        static Object next(PPermutations self) {
            self.setRaisedStopIteration(true);
            return iteratorExhausted();
        }

        @Specialization(guards = "!self.isStopped()")
        static Object next(PPermutations self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile isStartedProfile,
                        @Cached InlinedBranchProfile jProfile,
                        @Cached InlinedLoopConditionProfile resultLoopProfile,
                        @Cached InlinedLoopConditionProfile mainLoopProfile,
                        @Cached InlinedLoopConditionProfile shiftIndicesProfile,
                        @Bind PythonLanguage language) {
            int r = self.getR();

            int[] indices = self.getIndices();
            Object[] result = new Object[r];
            Object[] pool = self.getPool();
            resultLoopProfile.profileCounted(inliningTarget, r);
            for (int i = 0; resultLoopProfile.inject(inliningTarget, i < r); i++) {
                result[i] = pool[indices[i]];
            }

            int[] cycles = self.getCycles();
            int i = r - 1;
            while (mainLoopProfile.profile(inliningTarget, i >= 0)) {
                int j = cycles[i] - 1;
                if (j > 0) {
                    jProfile.enter(inliningTarget);
                    cycles[i] = j;
                    int tmp = indices[i];
                    indices[i] = indices[indices.length - j];
                    indices[indices.length - j] = tmp;
                    return PFactory.createTuple(language, result);
                }
                cycles[i] = indices.length - i;
                int n1 = indices.length - 1;
                assert n1 >= 0;
                int num = indices[i];
                shiftIndicesProfile.profileCounted(inliningTarget, n1 - i);
                for (int k = i; shiftIndicesProfile.profile(inliningTarget, k < n1); k++) {
                    indices[k] = indices[k + 1];
                }
                indices[n1] = num;
                i = i - 1;
            }

            self.setStopped(true);
            if (isStartedProfile.profile(inliningTarget, self.isStarted())) {
                return iteratorExhausted();
            } else {
                self.setStarted(true);
            }
            return PFactory.createTuple(language, result);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isRaisedStopIteration()")
        static Object reduce(PPermutations self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            Object type = getClassNode.execute(inliningTarget, self);
            PList poolList = PFactory.createList(language, self.getPool());
            PTuple tuple = PFactory.createTuple(language, new Object[]{poolList, self.getR()});

            // we must pickle the indices and use them for setstate
            PTuple indicesTuple = PFactory.createTuple(language, self.getIndices());
            PTuple cyclesTuple = PFactory.createTuple(language, self.getCycles());
            PTuple tuple2 = PFactory.createTuple(language, new Object[]{indicesTuple, cyclesTuple, self.isStarted()});

            Object[] result = new Object[]{type, tuple, tuple2};
            return PFactory.createTuple(language, result);
        }

        @Specialization(guards = "self.isRaisedStopIteration()")
        static Object reduceStopped(PPermutations self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple = PFactory.createTuple(language, new Object[]{PFactory.createEmptyTuple(language), self.getR()});
            Object[] result = new Object[]{type, tuple};
            return PFactory.createTuple(language, result);
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object setState(VirtualFrame frame, PPermutations self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached GetItemNode getItemNode,
                        @Cached InlinedLoopConditionProfile indicesProfile,
                        @Cached InlinedLoopConditionProfile cyclesProfile,
                        @Cached CastToJavaBooleanNode castBoolean,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (sizeNode.execute(frame, inliningTarget, state) != 3) {
                    throw raiseNode.raise(inliningTarget, ValueError, INVALID_ARGS, T___SETSTATE__);
                }
                Object indices = getItemNode.execute(frame, state, 0);
                Object cycles = getItemNode.execute(frame, state, 1);
                int poolLen = self.getPool().length;
                if (sizeNode.execute(frame, inliningTarget, indices) != poolLen || sizeNode.execute(frame, inliningTarget, cycles) != self.getR()) {
                    throw raiseNode.raise(inliningTarget, ValueError, INVALID_ARGS, T___SETSTATE__);
                }

                self.setStarted(castBoolean.execute(inliningTarget, getItemNode.execute(frame, state, 2)));
                indicesProfile.profileCounted(inliningTarget, poolLen);
                for (int i = 0; indicesProfile.inject(inliningTarget, i < poolLen); i++) {
                    int index = castInt.execute(inliningTarget, getItemNode.execute(frame, indices, i));
                    if (index < 0) {
                        index = 0;
                    } else if (index > poolLen - 1) {
                        index = poolLen - 1;
                    }
                    self.getIndices()[i] = index;
                }

                cyclesProfile.profileCounted(inliningTarget, self.getR());
                for (int i = 0; cyclesProfile.inject(inliningTarget, i < self.getR()); i++) {
                    int index = castInt.execute(inliningTarget, getItemNode.execute(frame, cycles, i));
                    if (index < 1) {
                        index = 1;
                    } else if (index > poolLen - i) {
                        index = poolLen - 1;
                    }
                    self.getCycles()[i] = index;
                }

                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
        }
    }

}
