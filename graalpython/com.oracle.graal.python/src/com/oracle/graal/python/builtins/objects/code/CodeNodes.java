/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.code;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.PRootNodeWithFileName;
import com.oracle.graal.python.nodes.util.BadOPCodeNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

public abstract class CodeNodes {

    public static class CreateCodeNode extends PNodeWithContext implements IndirectCallNode {
        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        @CompilationFinal private ContextReference<PythonContext> contextRef;
        @CompilationFinal private static Source emptySource;

        public PCode execute(VirtualFrame frame, Object cls, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codedata, Object[] constants, Object[] names,
                        Object[] varnames, Object[] freevars, Object[] cellvars,
                        String filename, String name, int firstlineno,
                        byte[] lnotab) {

            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);

            try {
                return createCode(context, cls, argcount,
                                posonlyargcount, kwonlyargcount, nlocals, stacksize, flags, codedata,
                                constants, names, varnames, freevars, cellvars, filename, name, firstlineno, lnotab);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @TruffleBoundary
        private PCode createCode(PythonContext context, Object cls, @SuppressWarnings("unused") int argcount,
                        @SuppressWarnings("unused") int posonlyargcount, @SuppressWarnings("unused") int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codedata, Object[] constants, Object[] names,
                        Object[] varnames, Object[] freevars, Object[] cellvars,
                        String filename, String name, int firstlineno,
                        byte[] lnotab) {

            Supplier<CallTarget> createCode = () -> {
                RootNode rootNode = context.getCore().getSerializer().deserialize(getEmptySource(), codedata, toStringArray(cellvars), toStringArray(freevars));
                if (rootNode instanceof BadOPCodeNode) {
                    ((BadOPCodeNode) rootNode).setName(name);
                }
                if (rootNode instanceof PRootNodeWithFileName) {
                    ((PRootNodeWithFileName) rootNode).setFileName(filename);
                }
                return PythonUtils.getOrCreateCallTarget(rootNode);
            };

            RootCallTarget ct = (RootCallTarget) createCode.get();
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            return factory.createCode(cls, ct, ((PRootNode) ct.getRootNode()).getSignature(), nlocals, stacksize, flags, codedata, constants, names, varnames, freevars, cellvars, filename, name,
                            firstlineno, lnotab);
        }

        public PCode execute(VirtualFrame frame, @SuppressWarnings("unused") Object cls, String sourceCode, int flags, byte[] codedata, String filename,
                        int firstlineno, byte[] lnotab) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return createCode(context, sourceCode, flags, codedata, filename, firstlineno, lnotab);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @TruffleBoundary
        private static PCode createCode(PythonContext context, String sourceCode, int flags, byte[] codedata, String filename,
                        int firstlineno, byte[] lnotab) {
            boolean isNotAModule = (flags & PCode.FLAG_MODULE) == 0;
            Source source = PythonLanguage.newSource(context, sourceCode, filename, isNotAModule);
            Supplier<CallTarget> createCode = () -> {
                RootNode rootNode = context.getCore().getSerializer().deserialize(source, codedata);
                return PythonUtils.getOrCreateCallTarget(rootNode);
            };

            RootCallTarget ct;
            if (context.getCore().isInitialized() || isNotAModule) {
                ct = (RootCallTarget) createCode.get();
            } else {
                ct = (RootCallTarget) context.getCore().getLanguage().cacheCode(filename, createCode);
            }
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            return factory.createCode(ct, codedata, flags, firstlineno, lnotab);
        }

        private ContextReference<PythonContext> getContextRef() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef;
        }

        private Source getEmptySource() {
            if (emptySource == null) {
                emptySource = createEmptySource();
            }
            return emptySource;
        }

        @TruffleBoundary
        private Source createEmptySource() {
            return PythonLanguage.newSource(getContextRef().get(), "", "unavailable", false);
        }

        @TruffleBoundary
        private static String[] toStringArray(Object[] array) {
            List<String> list = new ArrayList<>(array.length);
            for (Object item : array) {
                if (item instanceof String) {
                    list.add((String) item);
                }
            }
            return list.toArray(new String[list.size()]);
        }

        public static CreateCodeNode create() {
            return new CreateCodeNode();
        }
    }
}
