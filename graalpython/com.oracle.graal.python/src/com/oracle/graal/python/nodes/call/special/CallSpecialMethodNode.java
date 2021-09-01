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
package com.oracle.graal.python.nodes.call.special;

import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_D_ARGS;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PythonOptions.class, PGuards.class})
@NodeField(name = "maxSizeExceeded", type = boolean.class)
abstract class CallSpecialMethodNode extends Node {

    /**
     * for interpreter performance: cache if we exceeded the max caller size. We never allow
     * inlining in the uncached case.
     */
    protected abstract boolean isMaxSizeExceeded();

    protected abstract void setMaxSizeExceeded(boolean value);

    /**
     * Returns a new instanceof the builtin if it's a subclass of the given class, and null
     * otherwise.
     */
    private <T extends PythonBuiltinBaseNode> T getBuiltin(VirtualFrame frame, PBuiltinFunction func, Class<T> clazz) {
        CompilerAsserts.neverPartOfCompilation();
        NodeFactory<? extends PythonBuiltinBaseNode> builtinNodeFactory = func.getBuiltinNodeFactory();
        if (builtinNodeFactory == null) {
            return null; // see for example MethodDescriptorRoot and subclasses
        }
        assert builtinNodeFactory.getNodeClass().getAnnotationsByType(Builtin.class).length > 0 : "PBuiltinFunction " + func + " is expected to have a Builtin annotated node.";
        if (builtinNodeFactory.getNodeClass().getAnnotationsByType(Builtin.class)[0].needsFrame() && frame == null) {
            return null;
        }
        if (clazz.isAssignableFrom(builtinNodeFactory.getNodeClass())) {
            T builtinNode = clazz.cast(func.getBuiltinNodeFactory().createNode());
            if (!callerExceedsMaxSize(builtinNode)) {
                return builtinNode;
            }
        }
        return null;
    }

    private <T extends PythonBuiltinBaseNode> boolean callerExceedsMaxSize(T builtinNode) {
        CompilerAsserts.neverPartOfCompilation();
        if (isAdoptable() && !isMaxSizeExceeded()) {
            RootNode root = getRootNode();
            int n = root instanceof PRootNode ? ((PRootNode) root).getNodeCount() : NodeUtil.countNodes(root);
            // nb: option 'BuiltinsInliningMaxCallerSize' is defined as a compatible option, i.e.,
            // ASTs will only we shared between contexts that have the same value for this option.
            int maxSize = PythonLanguage.get(this).getEngineOption(PythonOptions.BuiltinsInliningMaxCallerSize);
            if (n >= maxSize || n + NodeUtil.countNodes(builtinNode) >= maxSize) {
                setMaxSizeExceeded(true);
                return true;
            }
            return false;
        }
        return true;
    }

    protected Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    protected static boolean frameIsUnused(PythonBuiltinBaseNode builtinNode) {
        return builtinNode == null || !builtinNode.getClass().getAnnotation(GeneratedBy.class).value().getAnnotationsByType(Builtin.class)[0].needsFrame();
    }

    PythonUnaryBuiltinNode getUnary(VirtualFrame frame, Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin(frame, (PBuiltinFunction) func, PythonUnaryBuiltinNode.class);
        }
        return null;
    }

    PythonBinaryBuiltinNode getBinary(VirtualFrame frame, Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin(frame, (PBuiltinFunction) func, PythonBinaryBuiltinNode.class);
        }
        return null;
    }

    PythonTernaryBuiltinNode getTernary(VirtualFrame frame, Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin(frame, (PBuiltinFunction) func, PythonTernaryBuiltinNode.class);
        }
        return null;
    }

    PythonQuaternaryBuiltinNode getQuaternary(VirtualFrame frame, Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin(frame, (PBuiltinFunction) func, PythonQuaternaryBuiltinNode.class);
        }
        return null;
    }

    PythonVarargsBuiltinNode getVarargs(VirtualFrame frame, Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin(frame, (PBuiltinFunction) func, PythonVarargsBuiltinNode.class);
        }
        return null;
    }

    protected static boolean takesSelfArg(Object func) {
        if (func instanceof PBuiltinFunction) {
            RootNode functionRootNode = ((PBuiltinFunction) func).getFunctionRootNode();
            if (functionRootNode instanceof BuiltinFunctionRootNode) {
                return ((BuiltinFunctionRootNode) functionRootNode).declaresExplicitSelf();
            }
        } else if (func instanceof PBuiltinMethod) {
            return takesSelfArg(((PBuiltinMethod) func).getFunction());
        }
        return true;
    }

    /**
     * Determines the minimum number of positional arguments accepted by the given built-in function
     * or method.
     */
    protected static int getMinArgs(Object func) {
        CompilerAsserts.neverPartOfCompilation();
        if (func instanceof PBuiltinFunction) {
            RootNode functionRootNode = ((PBuiltinFunction) func).getFunctionRootNode();
            if (functionRootNode instanceof BuiltinFunctionRootNode) {
                return ((BuiltinFunctionRootNode) functionRootNode).getBuiltin().minNumOfPositionalArgs();
            }
        } else if (func instanceof PBuiltinMethod) {
            return getMinArgs(((PBuiltinMethod) func).getFunction());
        }
        return 0;
    }

    protected static RootCallTarget getCallTarget(PMethod meth, GetCallTargetNode getCtNode) {
        return getCtNode.execute(meth.getFunction());
    }

    protected static RootCallTarget getCallTarget(PBuiltinMethod meth, GetCallTargetNode getCtNode) {
        return getCtNode.execute(meth.getFunction());
    }

    protected static boolean expectBooleanResult(Object value) throws UnexpectedResultException {
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        throw new UnexpectedResultException(value);
    }

    protected static double expectDoubleResult(Object value) throws UnexpectedResultException {
        if (value instanceof Double) {
            return (double) value;
        }
        throw new UnexpectedResultException(value);
    }

    protected static int expectIntegerResult(Object value) throws UnexpectedResultException {
        if (value instanceof Integer) {
            return (int) value;
        }
        throw new UnexpectedResultException(value);
    }

    protected static long expectLongResult(Object value) throws UnexpectedResultException {
        if (value instanceof Long) {
            return (long) value;
        }
        throw new UnexpectedResultException(value);
    }

    protected void raiseInvalidArgsNumUncached(boolean hasValidArgsNum, BuiltinMethodDescriptor descr) {
        if (!hasValidArgsNum) {
            raiseInvalidArgsNumUncached(descr);
        }
    }

    @TruffleBoundary
    private void raiseInvalidArgsNumUncached(BuiltinMethodDescriptor descr) {
        throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, EXPECTED_D_ARGS, descr.getBuiltinAnnotation().minNumOfPositionalArgs());
    }
}
