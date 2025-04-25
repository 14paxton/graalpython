/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.IsSameSlotNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotNbPower.CallSlotNbPowerNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateInline
@GenerateCached(value = false)
@GenerateUncached
public abstract class CallTernaryOpNode extends Node {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object classV, TpSlot slotV, Object w, Object classW, TpSlot slotW, Object z);

    @Specialization
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object v, Object classV, TpSlot slotV, Object w, Object classW, TpSlot slotWIn, Object z,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached IsSameSlotNode isSameSlotNode,
                    @Cached InlinedConditionProfile isSameTypeProfile,
                    @Cached InlinedConditionProfile isSameSlotProfile,
                    @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                    @Cached InlinedBranchProfile wResultBranch,
                    @Cached InlinedBranchProfile vResultBranch,
                    @Cached InlinedBranchProfile wResult2Branch,
                    @Cached InlinedBranchProfile notImplementedBranch,
                    @Cached CallSlotNbPowerNode callSlotWNode,
                    @Cached CallSlotNbPowerNode callSlotVNode,
                    @Cached CallSlotNbPowerNode callSlotZNode,
                    @Cached GetClassNode getZClass,
                    @Cached GetCachedTpSlotsNode getZSlots) {
        TpSlot slotW = null;
        boolean sameTypes = isSameTypeProfile.profile(inliningTarget, isSameTypeNode.execute(inliningTarget, classW, classV));
        if (!sameTypes) {
            slotW = slotWIn;
            if (isSameSlotProfile.profile(inliningTarget, slotV != null && slotW != null && isSameSlotNode.execute(inliningTarget, slotW, slotV))) {
                slotW = null;
            }
        }

        if (slotV != null) {
            if (slotW != null && isSubtypeNode.execute(classW, classV)) {
                assert !sameTypes;
                Object result = callSlotWNode.execute(frame, inliningTarget, slotW, v, classV, w, slotW, classW, z, false);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    wResultBranch.enter(inliningTarget);
                    return result;
                }
                slotW = null;
            }
            Object result = callSlotVNode.execute(frame, inliningTarget, slotV, v, classV, w, slotWIn, classW, z, sameTypes);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                vResultBranch.enter(inliningTarget);
                return result;
            }
        }

        if (slotW != null) {
            assert !sameTypes;
            Object result = callSlotWNode.execute(frame, inliningTarget, slotW, v, classV, w, slotW, classW, z, false);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                wResult2Branch.enter(inliningTarget);
                return result;
            }
        }

        if (z != PNone.NONE) {
            Object classZ = getZClass.execute(inliningTarget, z);
            TpSlot slotZ = getZSlots.execute(inliningTarget, classZ).nb_power();
            if (slotZ != null) {
                if ((slotV == null || !isSameSlotNode.execute(inliningTarget, slotZ, slotV)) && (slotW == null || !isSameSlotNode.execute(inliningTarget, slotZ, slotW))) {
                    return callSlotZNode.execute(frame, inliningTarget, slotZ, v, classV, w, slotWIn, classW, z, false);
                }
            }
        }

        notImplementedBranch.enter(inliningTarget);
        return PNotImplemented.NOT_IMPLEMENTED;
    }
}
