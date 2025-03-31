/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.referencetype;

import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.objectHashCode;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALLBACK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PReferenceType)
public final class ReferenceTypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ReferenceTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReferenceTypeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "ref", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object init(Object self, Object obj, Object callback) {
            return PNone.NONE;
        }
    }

    // ref.__callback__
    @Builtin(name = J___CALLBACK__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeCallbackPropertyNode extends PythonBuiltinNode {
        @Specialization
        public Object getCallback(PReferenceType self) {
            return self.getCallback();
        }
    }

    // ref.__call__()
    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeCallNode extends PythonBuiltinNode {
        @Specialization
        public Object call(PReferenceType self) {
            return self.getPyObject();
        }
    }

    // ref.__hash__
    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class RefTypeHashNode extends HashBuiltinNode {
        static long HASH_UNSET = -1;

        @Specialization(guards = "self.getHash() != HASH_UNSET")
        static long getHash(PReferenceType self) {
            return self.getHash();
        }

        @Specialization(guards = "self.getHash() == HASH_UNSET")
        static long computeHash(VirtualFrame frame, PReferenceType self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile referentProfile,
                        @Cached PyObjectHashNode hashNode,
                        @Cached PRaiseNode raiseNode) {
            Object referent = self.getObject();
            if (referentProfile.profile(inliningTarget, referent != null)) {
                long hash = hashNode.execute(frame, inliningTarget, referent);
                self.setHash(hash);
                return hash;
            } else {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.WEAK_OBJ_GONE_AWAY);
            }
        }

        @Fallback
        static long hashWrong(@SuppressWarnings("unused") Object self,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, "__hash__", "weakref", self);
        }
    }

    // ref.__repr__
    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class RefTypeReprNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getObject() == null")
        static TruffleString repr(PReferenceType self,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<weakref at %d; dead>", objectHashCode(self));
        }

        @Specialization(guards = "self.getObject() != null")
        static TruffleString repr(VirtualFrame frame, PReferenceType self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object object = self.getObject();
            Object cls = getClassNode.execute(inliningTarget, object);
            TruffleString className = getNameNode.execute(inliningTarget, cls);
            Object name = lookup.execute(frame, inliningTarget, object, T___NAME__);
            if (name == PNone.NO_VALUE) {
                return simpleTruffleStringFormatNode.format("<weakref at %d; to '%s' at %d>", objectHashCode(self), className, objectHashCode(object));
            } else {
                return simpleTruffleStringFormatNode.format("<weakref at %d; to '%s' at %d (%s)>", objectHashCode(self), className, objectHashCode(object), toStr(name));
            }
        }

        @TruffleBoundary
        private static String toStr(Object o) {
            // TODO GR-37980
            return o.toString();
        }
    }

    // ref.__eq__ and __ne__
    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class RefTypeEqNode extends RichCmpBuiltinNode {
        @Specialization(guards = {"self.getObject() != null", "other.getObject() != null", "op.isEqOrNe()"})
        static Object withObjs(VirtualFrame frame, PReferenceType self, PReferenceType other, RichCmpOp op,
                        @Bind("$node") Node inliningTarget,
                        @Cached PyObjectRichCompare richCompareNode) {
            return richCompareNode.execute(frame, inliningTarget, self.getObject(), other.getObject(), op);
        }

        @Specialization(guards = {"self.getObject() == null || other.getObject() == null", "op.isEqOrNe()"})
        static boolean withoutObjs(PReferenceType self, PReferenceType other, RichCmpOp op) {
            return (self == other) == op.isEq();
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object others(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
