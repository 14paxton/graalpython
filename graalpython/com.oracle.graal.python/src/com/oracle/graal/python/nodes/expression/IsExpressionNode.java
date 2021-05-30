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
package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.expression.IsExpressionNodeGen.IsNodeGen;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class IsExpressionNode extends BinaryOpNode {
    public static IsExpressionNode create(ExpressionNode left, ExpressionNode right) {
        return IsExpressionNodeGen.create(left, right);
    }

    @Specialization
    static boolean doIt(Object left, Object right,
                    @Cached IsNode isNode) {
        return isNode.execute(left, right);
    }

    @ImportStatic(PythonOptions.class)
    @GenerateUncached
    public abstract static class IsNode extends Node {

        protected abstract boolean executeInternal(Object left, Object right);

        public final boolean execute(Object left, Object right) {
            return left == right || executeInternal(left, right);
        }

        // Primitives
        @Specialization
        static boolean doBB(boolean left, boolean right) {
            return left == right;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doBI(boolean left, int right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doBL(boolean left, long right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doBD(boolean left, double right) {
            return false;
        }

        @Specialization
        static boolean doBP(boolean left, PInt right,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            Python3Core core = ctxt.getCore();
            if (left) {
                return right == core.getTrue();
            } else {
                return right == core.getFalse();
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doIB(int left, boolean right) {
            return false;
        }

        @Specialization
        static boolean doII(int left, int right) {
            return left == right;
        }

        @Specialization
        static boolean doIL(int left, long right) {
            return left == right;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doID(int left, double right) {
            return false;
        }

        @Specialization
        static boolean doIP(int left, PInt right,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            if (isBuiltin.profileIsAnyBuiltinObject(right)) {
                try {
                    return right.intValueExact() == left;
                } catch (OverflowException e) {
                    return false;
                }
            } else {
                return false;
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doLB(long left, boolean right) {
            return false;
        }

        @Specialization
        static boolean doLI(long left, int right) {
            return left == right;
        }

        @Specialization
        static boolean doLL(long left, long right) {
            return left == right;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doLD(long left, double right) {
            return false;
        }

        @Specialization
        static boolean doLP(long left, PInt right,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            if (isBuiltin.profileIsAnyBuiltinObject(right)) {
                try {
                    return left == right.longValueExact();
                } catch (OverflowException e) {
                    return false;
                }
            } else {
                return false;
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doDB(double left, boolean right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doDI(double left, int right) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization
        static boolean doDL(double left, long right) {
            return false;
        }

        @Specialization
        static boolean doDD(double left, double right) {
            // n.b. we simulate that the primitive NaN is a singleton; this is required to make
            // 'nan = float("nan"); nan is nan' work
            return left == right || (Double.isNaN(left) && Double.isNaN(right));
        }

        @Specialization
        static boolean doPB(PInt left, boolean right,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            return doBP(right, left, ctxt);
        }

        @Specialization
        static boolean doPI(PInt left, int right,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            return doIP(right, left, isBuiltin);
        }

        @Specialization
        static boolean doPL(PInt left, long right,
                        @Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
            return doLP(right, left, isBuiltin);
        }

        // types
        @Specialization
        static boolean doCT(PythonBuiltinClass left, PythonBuiltinClassType right) {
            return left.getType() == right;
        }

        @Specialization
        static boolean doTC(PythonBuiltinClassType left, PythonBuiltinClass right) {
            return right.getType() == left;
        }

        // native objects
        @Specialization
        static boolean doNative(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @Cached CExtNodes.PointerCompareNode isNode) {
            return isNode.execute(__EQ__, left, right);
        }

        // code
        @Specialization
        static boolean doCode(PCode left, PCode right) {
            // Special case for code objects: Frames create them on-demand even if they refer to the
            // same function. So we need to compare the root nodes.
            if (left != right) {
                RootCallTarget leftCt = left.getRootCallTarget();
                RootCallTarget rightCt = right.getRootCallTarget();
                if (leftCt != null && rightCt != null) {
                    // TODO: handle splitting, i.e., cloned root nodes
                    RootNode leftRootNode = leftCt.getRootNode();
                    RootNode rightRootNode = rightCt.getRootNode();
                    if (leftRootNode instanceof GeneratorFunctionRootNode) {
                        leftRootNode = ((GeneratorFunctionRootNode) leftRootNode).getFunctionRootNode();
                    }
                    if (rightRootNode instanceof GeneratorFunctionRootNode) {
                        rightRootNode = ((GeneratorFunctionRootNode) rightRootNode).getFunctionRootNode();
                    }
                    return leftRootNode == rightRootNode;
                } else {
                    return false;
                }
            }
            return true;
        }

        // none
        @Specialization
        static boolean doObjectPNone(Object left, PNone right,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            if (language.getEngineOption(PythonOptions.EmulateJython) && ctxt.getEnv().isHostObject(left) && ctxt.getEnv().asHostObject(left) == null &&
                            right == PNone.NONE) {
                return true;
            }
            return left == right;
        }

        @Specialization
        static boolean doPNoneObject(PNone left, Object right,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            return doObjectPNone(right, left, language, ctxt);
        }

        // pstring (may be interned)
        @Specialization(limit = "1")
        static boolean doPString(PString left, PString right,
                        @CachedLibrary("left") PythonObjectLibrary lib) {
            return lib.isSame(left, right);
        }

        // everything else
        @Specialization(guards = "isFallbackCase(left, right)")
        static boolean doOther(Object left, Object right,
                        @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            if (left == right) {
                return true;
            }
            if (isForeignObjectNode.execute(left)) {
                // If left is foreign, this will check its identity via the interop message. If left
                // is an object that is a wrapped Python object and uses a ReflectionLibrary, it
                // will not appear foreign, but the isSame call will unpack it from its wrapper and
                // may lead straight back to this node, but this time with the unwrapped Python
                // object that will no longer satisfy the isForeignObject condition.
                return lib.isSame(left, right);
            }
            return false;
        }

        private static boolean isPrimitive(Object object) {
            return object instanceof Boolean || object instanceof Integer || object instanceof Long || object instanceof Double;
        }

        private static boolean isBuiltinClassCase(Object left, Object right) {
            return left instanceof PythonBuiltinClassType && right instanceof PythonBuiltinClass;
        }

        static boolean isFallbackCase(Object left, Object right) {
            if (isPrimitive(left) && isPrimitive(right)) {
                return false;
            }
            if (left instanceof Boolean && right instanceof PInt || left instanceof PInt && right instanceof Boolean) {
                return false;
            }
            if (left instanceof Integer && right instanceof PInt || left instanceof PInt && right instanceof Integer) {
                return false;
            }
            if (left instanceof PythonAbstractNativeObject && right instanceof PythonAbstractNativeObject) {
                return false;
            }
            if (left instanceof PString && right instanceof PString) {
                return false;
            }
            if (isBuiltinClassCase(left, right) || isBuiltinClassCase(right, left)) {
                return false;
            }
            if (left instanceof PCode && right instanceof PCode) {
                return false;
            }
            if (left instanceof PNone || right instanceof PNone) {
                return false;
            }
            return true;
        }

        public static IsNode create() {
            return IsNodeGen.create();
        }
    }
}
