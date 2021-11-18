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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PProduct})
public final class ProductBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ProductBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PProduct self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!self.isStopped()", "!hasLst(self)"})
        Object next(VirtualFrame frame, PProduct self,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached LoopConditionProfile loopProfile) {
            Object[] lst = new Object[self.getGears().length];
            loopProfile.profileCounted(lst.length);
            for (int i = 0; loopProfile.inject(i < lst.length); i++) {
                lst[i] = getItemNode.execute(frame, self.getGears()[i], 0);
            }
            self.setLst(lst);
            return factory().createTuple(lst);
        }

        @Specialization(guards = {"!self.isStopped()", "hasLst(self)"})
        Object next(VirtualFrame frame, PProduct self,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached ConditionProfile gearsProfile,
                        @Cached ConditionProfile indexProfile,
                        @Cached BranchProfile wasStoppedProfile,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached BranchProfile doneProfile) {

            Object[] gears = self.getGears();
            int x = gears.length - 1;
            if (gearsProfile.profile(x >= 0)) {
                Object gear = gears[x];
                int[] indices = self.getIndices();
                int index = indices[x] + 1;
                if (indexProfile.profile(index < sizeNode.execute(frame, gear))) {
                    // no carry: done
                    self.getLst()[x] = getItemNode.execute(frame, gear, index);
                    indices[x] = index;
                } else {
                    rotatePreviousGear(frame, self, getItemNode, sizeNode, loopProfile, doneProfile);
                }
            } else {
                self.setStopped(true);
            }

            if (self.isStopped()) {
                wasStoppedProfile.enter();
                throw raise(StopIteration);
            }

            // the existing lst array can be changed in a following next call
            Object[] ret = new Object[self.getLst().length];
            PythonUtils.arraycopy(self.getLst(), 0, ret, 0, ret.length);
            return factory().createTuple(ret);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isStopped()")
        Object nextStopped(PProduct self) {
            throw raise(StopIteration);
        }

        private static void rotatePreviousGear(VirtualFrame frame, PProduct self, PyObjectGetItem getItemNode, PyObjectSizeNode sizeNode, LoopConditionProfile loopProfile, BranchProfile doneProfile) {
            Object[] lst = self.getLst();
            Object[] gears = self.getGears();
            int x = gears.length - 1;
            lst[x] = getItemNode.execute(frame, gears[x], 0);
            int[] indices = self.getIndices();
            indices[x] = 0;
            x = x - 1;
            // the outer loop runs as long as a we have a carry
            while (loopProfile.profile(x >= 0)) {
                Object gear = gears[x];
                int index = indices[x] + 1;
                if (index < sizeNode.execute(frame, gear)) {
                    // no carry: done
                    doneProfile.enter();
                    lst[x] = getItemNode.execute(frame, gear, index);
                    indices[x] = index;
                    return;
                }
                lst[x] = getItemNode.execute(frame, gear, 0);
                indices[x] = 0;
                x = x - 1;
            }
            self.setLst(null);
            self.setStopped(true);
        }

        protected static boolean hasLst(PProduct self) {
            return self.getLst() != null;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"!self.isStopped()", "!hasLst(self)"})
        Object reduce(PProduct self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            PTuple gearTuples = factory().createTuple(self.getGears());
            return factory().createTuple(new Object[]{type, gearTuples});
        }

        @Specialization(guards = {"!self.isStopped()", "hasLst(self)"})
        Object reduceLst(PProduct self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            PTuple gearTuples = factory().createTuple(self.getGears());
            PTuple indicesTuple = factory().createTuple(PythonUtils.arrayCopyOf(self.getIndices(), self.getIndices().length));
            return factory().createTuple(new Object[]{type, gearTuples, indicesTuple});
        }

        @Specialization(guards = "self.isStopped()")
        Object reduceStopped(PProduct self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            PTuple empty = factory().createEmptyTuple();
            return factory().createTuple(new Object[]{type, factory().createTuple(new Object[]{empty})});
        }

        protected static boolean hasLst(PProduct self) {
            return self.getLst() != null;
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(VirtualFrame frame, PProduct self, Object state,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached BranchProfile stoppedProfile,
                        @Cached ConditionProfile indexProfile) {
            Object[] gears = self.getGears();
            Object[] lst = new Object[gears.length];
            int[] indices = self.getIndices();
            loopProfile.profileCounted(gears.length);
            for (int i = 0; loopProfile.inject(i < gears.length); i++) {
                int index = (int) getItemNode.execute(frame, state, i);
                int gearSize = sizeNode.execute(frame, gears[i]);
                if (indices == null || gearSize == 0) {
                    stoppedProfile.enter();
                    self.setStopped(true);
                    return PNone.NONE;
                }
                if (indexProfile.profile(index < 0)) {
                    index = 0;
                } else if (index > gearSize - 1) {
                    index = gearSize - 1;
                }
                indices[i] = index;
                lst[i] = getItemNode.execute(frame, gears[i], index);
            }
            self.setLst(lst);
            return PNone.NONE;
        }
    }
}
