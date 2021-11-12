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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENTS_MUST_BE_ITERATORS;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.LenNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PChain})
public final class ChainBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ChainBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        Object init(VirtualFrame frame, PChain self, Object[] iterables,
                        @Cached PyObjectGetIter getIter) {
            self.setSource(getIter.execute(frame, factory().createList(iterables)));
            self.setActive(PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PChain self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(VirtualFrame frame, PChain self,
                        @Cached PyObjectGetIter getIter,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached IsBuiltinClassProfile isStopIterationProfile,
                        @Cached BranchProfile nextExceptioProfile,
                        @Cached LoopConditionProfile loopProfile) {
            while (loopProfile.profile(self.getSource() != PNone.NONE)) {
                if (self.getActive() == PNone.NONE) {
                    try {
                        Object next = nextNode.execute(frame, self.getSource(), PNone.NO_VALUE);
                        Object iter = getIter.execute(frame, next);
                        self.setActive(iter);
                    } catch (PException e) {
                        nextExceptioProfile.enter();
                        self.setSource(PNone.NONE);
                        throw e;
                    }
                }
                try {
                    return nextNode.execute(frame, self.getActive(), PNone.NO_VALUE);
                } catch (PException e) {
                    e.expectStopIteration(isStopIterationProfile);
                    self.setActive(PNone.NONE);
                }
            }
            throw raise(StopIteration);
        }
    }

    @Builtin(name = "from_iterable", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromIterNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object fromIter(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object arg,
                        @Cached PyObjectGetIter getIter) {
            PChain instance = factory().createChain();
            instance.setSource(getIter.execute(frame, arg));
            instance.setActive(PNone.NONE);
            return instance;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reducePos(PChain self,
                        @Cached GetClassNode getClass,
                        @Cached ConditionProfile hasSourceProfile,
                        @Cached ConditionProfile hasActiveProfile) {
            Object type = getClass.execute(self);
            PTuple empty = factory().createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            if (hasSourceProfile.profile(self.getSource() != PNone.NONE)) {
                if (hasActiveProfile.profile(self.getActive() != PNone.NONE)) {
                    PTuple tuple = factory().createTuple(new Object[]{self.getSource(), self.getActive()});
                    return factory().createTuple(new Object[]{type, empty, tuple});
                } else {
                    PTuple tuple = factory().createTuple(new Object[]{self.getSource()});
                    return factory().createTuple(new Object[]{type, empty, tuple});
                }
            } else {
                return factory().createTuple(new Object[]{type, empty});
            }
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        abstract Object execute(VirtualFrame frame, PythonObject self, Object state);

        @Specialization
        Object setState(VirtualFrame frame, PChain self, Object state,
                        @Cached LenNode lenNode,
                        @Cached GetItemNode getItemNode,
                        @Cached PyObjectLookupAttr getAttrNode,
                        @Cached BranchProfile isNotTupleProfile,
                        @Cached BranchProfile wrongLenProfile,
                        @Cached BranchProfile len2Profile,
                        @Cached BranchProfile sourceIteratorProfile,
                        @Cached BranchProfile activeIteratorProfile) {
            if (!(state instanceof PTuple)) {
                isNotTupleProfile.enter();
                throw raise(TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            int len = (int) lenNode.execute(frame, state);
            if (len < 1 || len > 2) {
                wrongLenProfile.enter();
                throw raise(TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            Object source = getItemNode.execute(frame, state, 0);
            checkIterator(getAttrNode, frame, source, sourceIteratorProfile);
            self.setSource(source);
            if (len == 2) {
                len2Profile.enter();
                Object active = getItemNode.execute(frame, state, 1);
                checkIterator(getAttrNode, frame, active, activeIteratorProfile);
                self.setActive(active);
            }
            return PNone.NONE;
        }

        private void checkIterator(PyObjectLookupAttr getAttrNode, VirtualFrame frame, Object active, BranchProfile profile) throws PException {
            if (getAttrNode.execute(frame, active, __NEXT__) == PNone.NO_VALUE) {
                profile.enter();
                throw raise(TypeError, ARGUMENTS_MUST_BE_ITERATORS);
            }
        }
    }

}
