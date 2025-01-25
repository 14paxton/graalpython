/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.processor;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.processor.SlotsProcessor.TpSlotData;

public class SlotsMapping {
    private static String getSuffix(boolean isComplex) {
        return isComplex ? "Complex" : "Simple";
    }

    static String getSlotBaseClass(Slot s) {
        return switch (s.value()) {
            case nb_bool -> "TpSlotInquiry.TpSlotInquiryBuiltin";
            case nb_index, nb_int, nb_float, nb_absolute, nb_positive, nb_negative, nb_invert -> "TpSlotUnaryFunc.TpSlotUnaryFuncBuiltin";
            case nb_add, nb_subtract, nb_multiply, nb_remainder, nb_divmod, nb_lshift, nb_rshift, nb_and, nb_xor, nb_or,
                            nb_floor_divide, nb_true_divide, nb_matrix_multiply ->
                "TpSlotBinaryOp.TpSlotBinaryOpBuiltin";
            case sq_concat -> "TpSlotBinaryFunc.TpSlotSqConcat";
            case sq_length, mp_length -> "TpSlotLen.TpSlotLenBuiltin" + getSuffix(s.isComplex());
            case sq_item, sq_repeat -> "TpSlotSizeArgFun.TpSlotSizeArgFunBuiltin";
            case sq_ass_item -> "TpSlotSqAssItem.TpSlotSqAssItemBuiltin";
            case mp_subscript -> "TpSlotBinaryFunc.TpSlotMpSubscript";
            case mp_ass_subscript -> "TpSlotMpAssSubscript.TpSlotMpAssSubscriptBuiltin";
            case tp_getattro -> "TpSlotGetAttr.TpSlotGetAttrBuiltin";
            case tp_descr_get -> "TpSlotDescrGet.TpSlotDescrGetBuiltin" + getSuffix(s.isComplex());
            case tp_descr_set -> "TpSlotDescrSet.TpSlotDescrSetBuiltin";
            case tp_setattro -> "TpSlotSetAttr.TpSlotSetAttrBuiltin";
        };
    }

    static String getSlotNodeBaseClass(Slot s) {
        return switch (s.value()) {
            case tp_descr_get -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode";
            case nb_bool -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode";
            case nb_index, nb_int, nb_float, nb_absolute, nb_positive, nb_negative, nb_invert -> "com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode";
            case nb_add, nb_subtract, nb_multiply, nb_remainder, nb_divmod, nb_lshift, nb_rshift, nb_and, nb_xor, nb_or,
                            nb_floor_divide, nb_true_divide, nb_matrix_multiply ->
                "com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode";
            case sq_concat -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.SqConcatBuiltinNode";
            case sq_length, mp_length -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode";
            case sq_item -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode";
            case sq_ass_item -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.SqAssItemBuiltinNode";
            case sq_repeat -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode";
            case mp_subscript -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode";
            case mp_ass_subscript -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode";
            case tp_getattro -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode";
            case tp_descr_set -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode";
            case tp_setattro -> "com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode";
        };
    }

    static String getUncachedExecuteSignature(SlotKind s) {
        return switch (s) {
            case nb_bool -> "boolean executeUncached(Object self)";
            case tp_descr_get -> "Object executeUncached(Object self, Object obj, Object type)";
            case sq_length, mp_length -> "int executeUncached(Object self)";
            default -> throw new AssertionError("Should not reach here: should be always complex");
        };
    }

    static boolean supportsComplex(SlotKind s) {
        return switch (s) {
            case nb_bool -> false;
            default -> true;
        };
    }

    static boolean supportsSimple(SlotKind s) {
        return switch (s) {
            case nb_bool, sq_length, mp_length, tp_descr_get -> true;
            default -> false;
        };
    }

    static String getUncachedExecuteCall(SlotKind s) {
        return switch (s) {
            case nb_bool -> "executeBool(null, self)";
            case sq_length, mp_length -> "executeInt(null, self)";
            case tp_descr_get -> "execute(null, self, obj, type)";
            default -> throw new AssertionError("Should not reach here: should be always complex");
        };
    }

    public static String getExtraCtorArgs(TpSlotData slot) {
        return switch (slot.slot().value()) {
            case nb_index -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___INDEX__";
            case nb_int -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___INT__";
            case nb_float -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOAT__";
            case nb_absolute -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___ABS__";
            case nb_positive -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___POS__";
            case nb_negative -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___NEG__";
            case nb_invert -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___INVERT__";
            case nb_add -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__";
            case nb_subtract -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__";
            case nb_multiply -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__";
            case nb_remainder -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___MOD__";
            case nb_divmod -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___DIVMOD__";
            case nb_lshift -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___LSHIFT__";
            case nb_rshift -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___RSHIFT__";
            case nb_and -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__";
            case nb_xor -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__";
            case nb_or -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__";
            case nb_floor_divide -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOORDIV__";
            case nb_true_divide -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUEDIV__";
            case nb_matrix_multiply -> ", com.oracle.graal.python.nodes.SpecialMethodNames.J___MATMUL__";
            default -> "";
        };
    }
}
