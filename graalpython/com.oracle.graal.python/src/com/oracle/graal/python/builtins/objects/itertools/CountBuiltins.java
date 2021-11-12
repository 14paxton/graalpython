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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.NUMBER_IS_REQUIRED;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import com.oracle.graal.python.annotations.ArgumentClinic;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCount})
public final class CountBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CountBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "start", "step"})
    @ArgumentClinic(name = "start", defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "step", defaultValue = "1", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CountBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object init(VirtualFrame frame, PCount self, Object start, Object step,
                        @Cached PyObjectTypeCheck typeCheckNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached BranchProfile startNumberProfile,
                        @Cached BranchProfile stepNumberProfile) {
            checkType(frame, start, typeCheckNode, lookupAttrNode, startNumberProfile);
            checkType(frame, step, typeCheckNode, lookupAttrNode, stepNumberProfile);
            self.setCnt(start);
            self.setStep(step);
            return PNone.NONE;
        }

        private void checkType(VirtualFrame frame, Object obj, PyObjectTypeCheck typeCheckNode, PyObjectLookupAttr lookupAttrNode, BranchProfile isNumberProfile) {
            if (typeCheckNode.execute(obj, PythonBuiltinClassType.PComplex) ||
                            lookupAttrNode.execute(frame, obj, __INDEX__) != PNone.NO_VALUE ||
                            lookupAttrNode.execute(frame, obj, __FLOAT__) != PNone.NO_VALUE ||
                            lookupAttrNode.execute(frame, obj, __INT__) != PNone.NO_VALUE) {
                isNumberProfile.enter();
                return;
            }
            throw raise(TypeError, NUMBER_IS_REQUIRED);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PCount self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object next(VirtualFrame frame, PCount self,
                        @Cached BinaryArithmetic.AddNode addNode) {
            Object cnt = self.getCnt();
            self.setCnt(addNode.executeObject(frame, self.getCnt(), self.getStep()));
            return cnt;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reprPos(VirtualFrame frame, PCount self,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached PyObjectReprAsObjectNode reprNode,
                        @Cached CastToJavaStringNode castStringNode,
                        @Cached CastToJavaLongExactNode castLongNode,
                        @Cached PyObjectTypeCheck typeCheckNode,
                        @Cached BranchProfile hasDefaultStep) {
            Object type = getClassNode.execute(self);
            StringBuilder sb = new StringBuilder();
            PythonUtils.append(sb, castStringNode.execute(getAttrNode.execute(frame, type, __NAME__)));
            PythonUtils.append(sb, "(");
            PythonUtils.append(sb, castStringNode.execute(reprNode.execute(frame, self.getCnt())));
            if (!typeCheckNode.execute(self.getStep(), PythonBuiltinClassType.PInt) || castLongNode.execute(self.getStep()) != 1) {
                hasDefaultStep.enter();
                PythonUtils.append(sb, ", ");
                PythonUtils.append(sb, castStringNode.execute(reprNode.execute(frame, self.getStep())));
            }
            PythonUtils.append(sb, ")");
            return PythonUtils.sbToString(sb);
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reducePos(PCount self,
                        @Cached GetClassNode getClassNode,
                        @Cached CastToJavaLongExactNode castLongNode,
                        @Cached PyObjectTypeCheck typeCheckNode,
                        @Cached ConditionProfile hasDefaultStep) {
            Object type = getClassNode.execute(self);
            PTuple tuple;
            if (hasDefaultStep.profile(!typeCheckNode.execute(self.getStep(), PythonBuiltinClassType.PInt) || castLongNode.execute(self.getStep()) != 1)) {
                tuple = factory().createTuple(new Object[]{self.getCnt(), self.getStep()});
            } else {
                tuple = factory().createTuple(new Object[]{self.getCnt()});
            }
            return factory().createTuple(new Object[]{type, tuple});
        }
    }
}
