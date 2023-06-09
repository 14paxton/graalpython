/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.asyncio;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PAsyncGen;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import java.util.List;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ANEXT__;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PAsyncGenerator)
public class AsyncGeneratorBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AsyncGeneratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = "ag_code", isGetter = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetCode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object getCode(PAsyncGen self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasCodeProfile) {
            return self.getOrCreateCode(inliningTarget, hasCodeProfile, factory());
        }
    }

    @Builtin(name = "ag_await", isGetter = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetAwait extends PythonUnaryBuiltinNode {
        @Specialization
        public Object getAwait(PAsyncGen self) {
            Object yieldFrom = self.getYieldFrom();
            return yieldFrom != null ? yieldFrom : PNone.NONE;
        }
    }

    @Builtin(name = "ag_frame", isGetter = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetFrame extends PythonUnaryBuiltinNode {
        @Specialization
        public Object getFrame(VirtualFrame frame, PAsyncGen self,
                        @Cached GeneratorBuiltins.GetFrameNode getFrame) {
            return getFrame.execute(frame, self);
        }
    }

    @Builtin(name = "ag_running", isGetter = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsRunning extends PythonUnaryBuiltinNode {
        @Specialization
        public boolean isRunning(PAsyncGen self) {
            return self.isRunning();
        }
    }

    @Builtin(name = "asend", declaresExplicitSelf = true, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ASend extends PythonBinaryBuiltinNode {
        @Specialization
        public Object aSend(PAsyncGen self, Object sent) {
            return factory().createAsyncGeneratorASend(self, sent);
        }
    }

    @Builtin(name = "athrow", declaresExplicitSelf = true, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class AThrow extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PAsyncGen self, Object arg1, Object arg2, Object arg3);

        @Specialization
        public Object athrow(PAsyncGen self, Object arg1, Object arg2, Object arg3) {
            return factory().createAsyncGeneratorAThrow(self, arg1, arg2, arg3);
        }
    }

    @Builtin(name = J___AITER__, declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AIter extends PythonUnaryBuiltinNode {
        @Specialization
        public Object aIter(PAsyncGen self) {
            return self;
        }
    }

    @Builtin(name = J___ANEXT__, declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ANext extends PythonUnaryBuiltinNode {
        @Specialization
        public Object aNext(PAsyncGen self) {
            return factory().createAsyncGeneratorASend(self, PNone.NONE);
        }
    }

    @Builtin(name = "aclose", declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AClose extends PythonUnaryBuiltinNode {
        @Specialization
        public Object aClose(PAsyncGen self) {
            return factory().createAsyncGeneratorAThrow(self, null, PNone.NO_VALUE, PNone.NO_VALUE);
        }
    }
}
