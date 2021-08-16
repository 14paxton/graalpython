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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.TernaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode.BoundDescriptor;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
public abstract class CallBinaryMethodNode extends CallReversibleMethodNode {
    public static CallBinaryMethodNode create() {
        return CallBinaryMethodNodeGen.create();
    }

    public static CallBinaryMethodNode getUncached() {
        return CallBinaryMethodNodeGen.getUncached();
    }

    public abstract boolean executeBool(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract int executeInt(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract long executeLong(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract Object executeObject(Frame frame, Object callable, Object arg1, Object arg2);

    public final Object executeObject(Object callable, Object arg1, Object arg2) {
        return executeObject(null, callable, arg1, arg2);
    }

    @Specialization(guards = "cachedInfo == info", limit = "getCallSiteInlineCacheMaxDepth()")
    Object callBinarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") BinaryBuiltinDescriptor info, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("info") BinaryBuiltinDescriptor cachedInfo,
                    @Cached("cachedInfo.createNode()") PythonBinaryBuiltinNode node) {
        return node.call(frame, arg1, arg2);
    }

    @TruffleBoundary(allowInlining = true)
    protected static boolean hasAllowedArgsNum(BuiltinMethodDescriptor descr) {
        return descr.getBuiltinAnnotation().minNumOfPositionalArgs() <= 2;
    }

    @Specialization(guards = "cachedInfo == info", limit = "getCallSiteInlineCacheMaxDepth()")
    Object callTernarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") TernaryBuiltinDescriptor info, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("info") TernaryBuiltinDescriptor cachedInfo,
                    @Cached("hasAllowedArgsNum(cachedInfo)") boolean hasValidArgsNum,
                    @Cached("cachedInfo.createNode()") PythonTernaryBuiltinNode node) {
        raiseInvalidArgsNumUncached(hasValidArgsNum, cachedInfo);
        return node.call(frame, arg1, arg2, PNone.NO_VALUE);
    }

    protected static boolean isBinaryOrTernaryBuiltinDescriptor(Object value) {
        return value instanceof BinaryBuiltinDescriptor || value instanceof TernaryBuiltinDescriptor;
    }

    @Specialization(guards = "isBinaryOrTernaryBuiltinDescriptor(info)", replaces = {"callBinarySpecialMethodSlotInlined", "callTernarySpecialMethodSlotInlined"})
    Object callSpecialMethodSlotCallTarget(VirtualFrame frame, BuiltinMethodDescriptor info, Object arg1, Object arg2,
                    @Cached ConditionProfile invalidArgsProfile,
                    @Cached GenericInvokeNode invokeNode) {
        raiseInvalidArgsNumUncached(invalidArgsProfile.profile(hasAllowedArgsNum(info)), info);
        RootCallTarget callTarget = PythonLanguage.get(this).getDescriptorCallTarget(info);
        Object[] arguments = PArguments.create(2);
        PArguments.setArgument(arguments, 0, arg1);
        PArguments.setArgument(arguments, 1, arg2);
        return invokeNode.execute(frame, callTarget, arguments);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean arg1, boolean arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBool(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean arg1, boolean arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    int callIntSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callInt(frame, arg2, arg1);
        } else {
            return builtinNode.callInt(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callInt(frame, arg2, arg1);
        } else {
            return builtinNode.callInt(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolIntSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolInt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    long callLongSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callLong(frame, arg2, arg1);
        } else {
            return builtinNode.callLong(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    long callLong(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callLong(frame, arg2, arg1);
        } else {
            return builtinNode.callLong(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolLongSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolLong(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    double callDoubleSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callDouble(frame, arg2, arg1);
        } else {
            return builtinNode.callDouble(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    double callDouble(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callDouble(frame, arg2, arg1);
        } else {
            return builtinNode.callDouble(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolDoubleSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolDouble(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        if (isReverse) {
            return builtinNode.callBool(frame, arg2, arg1);
        } else {
            return builtinNode.callBool(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callObjectSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        if (isReverse) {
            return builtinNode.call(frame, arg2, arg1);
        } else {
            return builtinNode.call(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callObject(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        if (isReverse) {
            return builtinNode.call(frame, arg2, arg1);
        } else {
            return builtinNode.call(frame, arg1, arg2);
        }
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "!takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBinary(frame, func.getFunction())") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callSelfMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached(value = "func", weak = true) PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getTernary(frame, func.getFunction())") PythonTernaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, func.getSelf(), arg1, arg2);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "!takesSelfArg", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBinary(frame, func.getFunction())") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "takesSelfArg", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callSelfMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getTernary(frame, func.getFunction())") PythonTernaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, func.getSelf(), arg1, arg2);
    }

    /**
     * In case the function takes less or equal to 2 arguments (so it is <it>at least</it> binary)
     * we also try to call a ternary function.
     */
    @Specialization(guards = {"builtinNode != null", "minArgs <= 2", "func.getCallTarget() == ct", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callTernaryFunction(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("getMinArgs(func)") int minArgs,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(ct)") boolean isReverse,
                    @Cached("getTernary(frame, func)") PythonTernaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        if (isReverse) {
            return builtinNode.call(frame, arg2, arg1, PNone.NO_VALUE);
        }
        return builtinNode.call(frame, arg1, arg2, PNone.NO_VALUE);
    }

    /**
     * In case the function takes less or equal to 2 arguments (so it is <it>at least</it> binary)
     * we also try to call a quaternary function.
     */
    @Specialization(guards = {"builtinNode != null", "minArgs <= 2", "func.getCallTarget() == ct", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callQuaternaryFunction(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("getMinArgs(func)") int minArgs,
                    @Cached("getQuaternary(frame, func)") PythonQuaternaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2, PNone.NO_VALUE, PNone.NO_VALUE);
    }

    @Specialization(guards = "!isBinaryOrTernaryBuiltinDescriptor(func)", //
                    replaces = {"callBoolSingle", "callBool", "callIntSingle", "callInt", "callBoolIntSingle", "callBoolInt", "callLongSingle", "callLong", "callBoolLongSingle",
                                    "callBoolLong", "callDoubleSingle", "callDouble", "callBoolDoubleSingle", "callBoolDouble", "callObjectSingleContext", "callObject",
                                    "callMethodSingleContext", "callSelfMethodSingleContext", "callMethod", "callSelfMethod", "callTernaryFunction", "callQuaternaryFunction"})
    @Megamorphic
    static Object call(VirtualFrame frame, Object func, Object arg1, Object arg2,
                    @Cached CallNode callNode,
                    @Cached ConditionProfile isBoundProfile) {
        if (isBoundProfile.profile(func instanceof BoundDescriptor)) {
            return callNode.execute(frame, ((BoundDescriptor) func).descriptor, new Object[]{arg2}, PKeyword.EMPTY_KEYWORDS);
        } else {
            return callNode.execute(frame, func, new Object[]{arg1, arg2}, PKeyword.EMPTY_KEYWORDS);
        }
    }
}
