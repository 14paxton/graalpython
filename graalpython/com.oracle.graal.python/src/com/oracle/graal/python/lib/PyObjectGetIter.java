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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent PyObject_GetIter
 */
@GenerateUncached
@GenerateCached
@GenerateInline(inlineByDefault = true)
public abstract class PyObjectGetIter extends Node {
    public static Object executeUncached(Object obj) {
        return PyObjectGetIterNodeGen.getUncached().execute(null, null, obj);
    }

    public final Object executeCached(Frame frame, Object receiver) {
        return execute(frame, this, receiver);
    }

    public abstract Object execute(Frame frame, Node inliningTarget, Object receiver);

    @Specialization
    static Object getIterRange(PIntRange object,
                    @Bind PythonLanguage language) {
        return PFactory.createIntRangeIterator(language, object);
    }

    @Specialization
    @InliningCutoff
    static Object getIter(VirtualFrame frame, Node inliningTarget, Object receiver,
                    @Cached GetClassNode getReceiverClass,
                    @Cached GetCachedTpSlotsNode getSlots,
                    @Cached PySequenceCheckNode sequenceCheckNode,
                    @Cached PRaiseNode raise,
                    @Cached CallSlotUnaryNode callSlot,
                    @Cached PyIterCheckNode checkNode) {
        Object type = getReceiverClass.execute(inliningTarget, receiver);
        TpSlots slots = getSlots.execute(inliningTarget, type);
        if (slots.tp_iter() != null) {
            Object result = callSlot.execute(frame, inliningTarget, slots.tp_iter(), receiver);
            if (!checkNode.execute(inliningTarget, result)) {
                throw raise.raise(inliningTarget, TypeError, ErrorMessages.RETURNED_NONITER, result);
            }
            return result;
        } else {
            if (sequenceCheckNode.execute(inliningTarget, receiver)) {
                return PFactory.createSequenceIterator(PythonLanguage.get(inliningTarget), receiver);
            }
        }
        throw raise.raise(inliningTarget, TypeError, ErrorMessages.OBJ_NOT_ITERABLE, receiver);
    }

    @NeverDefault
    public static PyObjectGetIter create() {
        return PyObjectGetIterNodeGen.create();
    }

    public static PyObjectGetIter getUncached() {
        return PyObjectGetIterNodeGen.getUncached();
    }
}
