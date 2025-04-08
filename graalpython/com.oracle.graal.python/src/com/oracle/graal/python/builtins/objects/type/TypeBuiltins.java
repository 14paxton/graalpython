/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyHeapTypeObject__ht_name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyHeapTypeObject__ht_qualname;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_name;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FLAGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MRO__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___WEAKREFOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSHOOK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_UPDATE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructorsFactory;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBestBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInit.CallSlotTpInitNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInit.TpSlotInitBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.types.GenericTypeNodes;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectGetBasesNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectIsSubclassNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonClass)
public final class TypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = TypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T___MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(T___QUALNAME__)") GetFixedAttributeNode readQualNameNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object moduleNameObj = readModuleNode.executeObject(frame, self);
            Object qualNameObj = readQualNameNode.executeObject(frame, self);
            TruffleString moduleName = null;
            if (moduleNameObj != NO_VALUE) {
                try {
                    moduleName = castToStringNode.execute(inliningTarget, moduleNameObj);
                } catch (CannotCastException e) {
                    // ignore
                }
            }
            if (moduleName == null || equalNode.execute(moduleName, T_BUILTINS, TS_ENCODING)) {
                return simpleTruffleStringFormatNode.format("<class '%s'>", castToStringNode.execute(inliningTarget, qualNameObj));
            }
            return simpleTruffleStringFormatNode.format("<class '%s.%s'>", moduleName, castToStringNode.execute(inliningTarget, qualNameObj));
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class DocNode extends PythonBinaryBuiltinNode {

        private static final TruffleString BUILTIN_DOC = tsLiteral("type(object_or_name, bases, dict)\n" + //
                        "type(object) -> the object's type\n" + //
                        "type(name, bases, dict) -> a new type");

        @Specialization(guards = "isNoValue(value)")
        Object getDoc(PythonBuiltinClassType self, @SuppressWarnings("unused") PNone value) {
            return getDoc(getContext().lookupType(self), value);
        }

        @Specialization(guards = "isNoValue(value)")
        @TruffleBoundary
        static Object getDoc(PythonBuiltinClass self, @SuppressWarnings("unused") PNone value) {
            // see type.c#type_get_doc()
            if (IsBuiltinClassExactProfile.profileClassSlowPath(self, PythonBuiltinClassType.PythonClass)) {
                return BUILTIN_DOC;
            } else {
                return self.getAttribute(T___DOC__);
            }
        }

        @Specialization(guards = {"isNoValue(value)", "!isPythonBuiltinClass(self)"})
        static Object getDoc(VirtualFrame frame, PythonClass self, @SuppressWarnings("unused") PNone value) {
            // see type.c#type_get_doc()
            Object res = self.getAttribute(T___DOC__);
            Object resClass = GetClassNode.executeUncached(res);
            Object get = LookupAttributeInMRONode.Dynamic.getUncached().execute(resClass, T___GET__);
            if (PGuards.isCallable(get)) {
                return CallTernaryMethodNode.getUncached().execute(frame, get, res, PNone.NONE, self);
            }
            return res;
        }

        @Specialization
        static Object getDoc(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone value) {
            return ReadAttributeFromObjectNode.getUncachedForceType().execute(self, T___DOC__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "!isPythonBuiltinClass(self)"})
        static Object setDoc(PythonClass self, Object value) {
            self.setAttribute(T___DOC__, value);
            return NO_VALUE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "isKindOfBuiltinClass(self)"})
        static Object doc(Object self, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___DOC__, self);
        }

        @Specialization
        static Object doc(Object self, @SuppressWarnings("unused") DescriptorDeleteMarker marker,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_DELETE_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___DOC__, self);
        }
    }

    @Builtin(name = J___MRO__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(Object klass,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetMroNode getMroNode,
                        @Cached InlinedConditionProfile notInitialized) {
            if (notInitialized.profile(inliningTarget, klass instanceof PythonManagedClass && !((PythonManagedClass) klass).isMROInitialized())) {
                return PNone.NONE;
            }
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, klass);
            return PFactory.createTuple(PythonLanguage.get(inliningTarget), mro);
        }
    }

    @Builtin(name = J_MRO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isTypeNode.execute(inliningTarget, klass)", limit = "1")
        static Object doit(Object klass,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetMroNode getMroNode) {
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, klass);
            return PFactory.createList(PythonLanguage.get(inliningTarget), Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doit(Object object,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T_MRO, "type", object);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object init(@SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (arguments.length != 1 && arguments.length != 3) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type.__init__()", 1, 3);
            }
            if (arguments.length == 1 && kwds.length != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "type.__init__()");
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached CreateInstanceNode createInstanceNode) {
            if (isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PythonClass, self)) {
                if (arguments.length == 1 && keywords.length == 0) {
                    return getClassNode.execute(inliningTarget, arguments[0]);
                } else if (arguments.length != 3) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
                }
            }
            return createInstanceNode.execute(frame, inliningTarget, self, arguments, keywords);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class BindNew extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object descriptor, Object type);

        @Specialization
        static Object doBuiltinMethod(PBuiltinMethod descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Specialization
        static Object doBuiltinDescriptor(BuiltinMethodDescriptor descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Specialization
        static Object doFunction(PFunction descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Fallback
        static Object doBind(VirtualFrame frame, Node inliningTarget, Object descriptor, Object type,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached CallSlotDescrGet callGetSlot) {
            var getMethod = getSlotsNode.execute(inliningTarget, descriptor).tp_descr_get();
            if (getMethod != null) {
                return callGetSlot.execute(frame, inliningTarget, getMethod, descriptor, NO_VALUE, type);
            }
            return descriptor;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CheckTypeFlagsNode extends Node {
        abstract void execute(Node inliningTarget, Object type);

        @Specialization
        static void doPBCT(Node inliningTarget, PythonBuiltinClassType type,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (type.disallowInstantiation()) {
                throw raiseException(inliningTarget, type, raiseNode);
            }
        }

        @Specialization
        static void doNative(Node inliningTarget, PythonAbstractNativeObject type,
                        @Cached GetTypeFlagsNode getTypeFlagsNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            if ((getTypeFlagsNode.execute(type) & TypeFlags.DISALLOW_INSTANTIATION) != 0) {
                throw raiseException(inliningTarget, type, raiseNode);
            }
        }

        @Fallback
        static void doManaged(@SuppressWarnings("unused") Object type) {
            // Guaranteed by caller
            assert !(type instanceof PythonBuiltinClass);
        }

        @InliningCutoff
        private static PException raiseException(Node inliningTarget, PythonAbstractNativeObject type, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_CREATE_N_INSTANCES, type);
        }

        @InliningCutoff
        private static PException raiseException(Node inliningTarget, PythonBuiltinClassType type, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, type.getPrintName());
        }
    }

    @GenerateInline
    @GenerateCached(false)
    protected abstract static class CreateInstanceNode extends PNodeWithContext {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object self, Object[] args, PKeyword[] keywords);

        @Specialization
        static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object type, Object[] arguments, PKeyword[] keywords,
                        @Cached CheckTypeFlagsNode checkTypeFlagsNode,
                        @Cached InlinedConditionProfile builtinProfile,
                        @Cached GetCachedTpSlotsNode getSlots,
                        @Cached(parameters = "New") LookupCallableSlotInMRONode lookupNew,
                        @Cached BindNew bindNew,
                        @Cached CallVarargsMethodNode dispatchNew,
                        @Cached GetClassNode getInstanceClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CallSlotTpInitNode callInit) {
            if (builtinProfile.profile(inliningTarget, type instanceof PythonBuiltinClass)) {
                // PythonBuiltinClassType should help the code after this to optimize better
                type = ((PythonBuiltinClass) type).getType();
            }
            checkTypeFlagsNode.execute(inliningTarget, type);
            Object newMethod = lookupNew.execute(type);
            assert newMethod != NO_VALUE;
            Object[] newArgs = PythonUtils.prependArgument(type, arguments);
            Object newInstance = dispatchNew.execute(frame, bindNew.execute(frame, inliningTarget, newMethod, type), newArgs, keywords);
            Object newInstanceKlass = getInstanceClassNode.execute(inliningTarget, newInstance);
            if (isSubtypeNode.execute(newInstanceKlass, type)) {
                TpSlots slots = getSlots.execute(inliningTarget, newInstanceKlass);
                if (slots.tp_init() != null) {
                    callInit.execute(frame, inliningTarget, slots.tp_init(), newInstance, arguments, keywords);
                }
            }
            return newInstance;
        }
    }

    @ImportStatic(PGuards.class)
    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends GetAttrBuiltinNode {
        @Child private CallSlotDescrGet callSlotDescrGet;
        @Child private CallSlotDescrGet callSlotValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;

        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached GetObjectSlotsNode getValueSlotsNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached CastToTruffleStringNode castToString,
                        @Cached InlinedBranchProfile hasDescProfile,
                        @Cached InlinedConditionProfile hasDescrGetProfile,
                        @Cached InlinedBranchProfile hasValueProfile,
                        @Cached InlinedBranchProfile hasNonDescriptorValueProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            TruffleString key;
            try {
                key = castToString.execute(inliningTarget, keyObj);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object metatype = getClassNode.execute(inliningTarget, object);
            Object descr = lookup.execute(metatype, key);
            TpSlot get = null;
            boolean hasDescrGet = false;
            if (descr != NO_VALUE) {
                // acts as a branch profile
                var descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
                get = descrSlots.tp_descr_get();
                hasDescrGet = hasDescrGetProfile.profile(inliningTarget, get != null);
                if (hasDescrGet && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    return dispatchDescrGet(frame, object, metatype, descr, get);
                }
            }
            Object value = readAttribute(object, key);
            if (value != NO_VALUE) {
                hasValueProfile.enter(inliningTarget);
                var valueSlots = getValueSlotsNode.execute(inliningTarget, value);
                var valueGet = valueSlots.tp_descr_get();
                if (valueGet == null) {
                    hasNonDescriptorValueProfile.enter(inliningTarget);
                    return value;
                } else {
                    return dispatchValueGet(frame, object, value, valueGet);
                }
            }
            if (descr != NO_VALUE) {
                hasDescProfile.enter(inliningTarget);
                if (!hasDescrGet) {
                    return descr;
                } else {
                    return dispatchDescrGet(frame, object, metatype, descr, get);
                }
            }
            errorProfile.enter(inliningTarget);
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.OBJ_N_HAS_NO_ATTR_S, object, key);
        }

        private Object readAttribute(Object object, TruffleString key) {
            if (lookupAsClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAsClass = insert(LookupAttributeInMRONode.Dynamic.create());
            }
            return lookupAsClass.execute(object, key);
        }

        private Object dispatchDescrGet(VirtualFrame frame, Object object, Object type, Object descr, TpSlot getSlot) {
            if (callSlotDescrGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSlotDescrGet = insert(CallSlotDescrGet.create());
            }
            return callSlotDescrGet.executeCached(frame, getSlot, descr, object, type);
        }

        private Object dispatchValueGet(VirtualFrame frame, Object type, Object descr, TpSlot getSlot) {
            if (callSlotValueGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSlotValueGet = insert(CallSlotDescrGet.create());
            }
            // NO_VALUE 2nd argument indicates the descriptor was found on the target object itself
            // (or a base)
            return callSlotValueGet.executeCached(frame, getSlot, descr, NO_VALUE, type);
        }
    }

    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization(guards = "!isImmutable(object)")
        void setString(VirtualFrame frame, Object object, TruffleString key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Shared @Cached("createForceType()") WriteAttributeToObjectNode write) {
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
        }

        @Specialization(guards = "!isImmutable(object)")
        @InliningCutoff
        static void set(VirtualFrame frame, Object object, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Shared @Cached("createForceType()") WriteAttributeToObjectNode write) {
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
        }

        @Specialization(guards = "isImmutable(object)")
        @TruffleBoundary
        void setBuiltin(Object object, Object key, Object value) {
            if (PythonContext.get(this).isInitialized()) {
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(key), object);
            } else {
                set(null, object, key, value, null, ObjectNodes.GenericSetAttrNode.getUncached(), WriteAttributeToObjectNode.getUncached(true));
            }
        }

        protected static boolean isImmutable(Object type) {
            // TODO should also check Py_TPFLAGS_IMMUTABLETYPE
            return type instanceof PythonBuiltinClass || type instanceof PythonBuiltinClassType;
        }
    }

    @Builtin(name = J___PREPARE__, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class PrepareNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doIt(Object args, Object kwargs,
                        @Bind PythonLanguage language) {
            return PFactory.createDict(language, new DynamicObjectStorage(language));
        }
    }

    @Builtin(name = J___BASES__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object getBases(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetBaseClassesNode getBaseClassesNode) {
            return PFactory.createTuple(language, getBaseClassesNode.execute(inliningTarget, self));
        }

        @Specialization
        static Object setBases(VirtualFrame frame, PythonClass cls, PTuple value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNameNode getName,
                        @Cached GetObjectArrayNode getArray,
                        @Cached GetBaseClassNode getBase,
                        @Cached GetBestBaseClassNode getBestBase,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigment,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetMroNode getMroNode,
                        @Cached PRaiseNode raiseNode) {

            Object[] a = getArray.execute(inliningTarget, value);
            if (a.length == 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_ASSIGN_NON_EMPTY_TUPLE_TO_P, cls);
            }
            PythonAbstractClass[] baseClasses = new PythonAbstractClass[a.length];
            for (int i = 0; i < a.length; i++) {
                if (PGuards.isPythonClass(a[i])) {
                    if (isSubtypeNode.execute(frame, a[i], cls) ||
                                    hasMRO(inliningTarget, getMroNode, a[i]) && typeIsSubtypeBaseChain(inliningTarget, a[i], cls, getBase, isSameTypeNode)) {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.BASES_ITEM_CAUSES_INHERITANCE_CYCLE);
                    }
                    if (a[i] instanceof PythonBuiltinClassType) {
                        baseClasses[i] = PythonContext.get(inliningTarget).lookupType((PythonBuiltinClassType) a[i]);
                    } else {
                        baseClasses[i] = (PythonAbstractClass) a[i];
                    }
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_TUPLE_OF_CLASSES_NOT_P, getName.execute(inliningTarget, cls), "__bases__", a[i]);
                }
            }

            Object newBestBase = getBestBase.execute(inliningTarget, baseClasses);
            if (newBestBase == null) {
                return null;
            }

            Object oldBase = getBase.execute(inliningTarget, cls);
            checkCompatibleForAssigment.execute(frame, oldBase, newBestBase);

            cls.setBases(inliningTarget, newBestBase, baseClasses);
            SpecialMethodSlot.reinitializeSpecialMethodSlots(cls, PythonLanguage.get(inliningTarget));
            TpSlots.updateAllSlots(cls);

            return PNone.NONE;
        }

        private static boolean hasMRO(Node inliningTarget, GetMroNode getMroNode, Object i) {
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, i);
            return mro != null && mro.length > 0;
        }

        private static boolean typeIsSubtypeBaseChain(Node inliningTarget, Object a, Object b, GetBaseClassNode getBaseNode, IsSameTypeNode isSameTypeNode) {
            Object base = a;
            do {
                if (isSameTypeNode.execute(inliningTarget, base, b)) {
                    return true;
                }
                base = getBaseNode.execute(inliningTarget, base);
            } while (base != null);

            return (isSameTypeNode.execute(inliningTarget, b, PythonBuiltinClassType.PythonObject));
        }

        @Specialization(guards = "!isPTuple(value)")
        static Object setObject(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_S_S_NOT_P, "tuple", GetNameNode.executeUncached(cls), "__bases__", value);
        }

        @Specialization
        static Object setBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___BASES__, cls);
        }

    }

    @Builtin(name = J___BASE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BaseNode extends PythonBuiltinNode {
        @Specialization
        static Object base(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetBaseClassNode getBaseClassNode) {
            Object baseClass = getBaseClassNode.execute(inliningTarget, self);
            return baseClass != null ? baseClass : PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doType(PythonBuiltinClassType self,
                        @Bind PythonLanguage language,
                        @Shared @Cached GetDictIfExistsNode getDict) {
            return doManaged(getContext().lookupType(self), language, getDict);
        }

        @Specialization
        static Object doManaged(PythonManagedClass self,
                        @Bind PythonLanguage language,
                        @Shared @Cached GetDictIfExistsNode getDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                dict = PFactory.createDictFixedStorage(language, self, self.getMethodResolutionOrder());
                // The mapping is unmodifiable, so we don't have to assign it back
            }
            return PFactory.createMappingproxy(language, dict);
        }

        @Specialization
        static Object doNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadObjectNode getTpDictNode) {
            return getTpDictNode.readFromObj(self, CFields.PyTypeObject__tp_dict);
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Child private PyObjectLookupAttr getAttributeNode;

        public abstract boolean executeWith(VirtualFrame frame, Object cls, Object instance);

        public PyObjectLookupAttr getGetAttributeNode() {
            if (getAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttributeNode = insert(PyObjectLookupAttr.create());
            }
            return getAttributeNode;
        }

        private PythonObject getInstanceClassAttr(VirtualFrame frame, Object instance) {
            Object classAttr = getGetAttributeNode().executeCached(frame, instance, T___CLASS__);
            if (classAttr instanceof PythonObject) {
                return (PythonObject) classAttr;
            }
            return null;
        }

        @Specialization(guards = "isTypeNode.execute(inliningTarget, cls)", limit = "1")
        @SuppressWarnings("truffle-static-method")
        boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            if (instance instanceof PythonObject && isSubtypeNode.execute(frame, getClassNode.execute(inliningTarget, instance), cls)) {
                return true;
            }

            Object instanceClass = getGetAttributeNode().executeCached(frame, instance, T___CLASS__);
            return PGuards.isManagedClass(instanceClass) && isSubtypeNode.execute(frame, instanceClass, cls);
        }

        @Fallback
        @SuppressWarnings("truffle-static-method")
        boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile typeErrorProfile,
                        @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                        @Cached AbstractObjectGetBasesNode getBasesNode,
                        @Cached PRaiseNode raiseNode) {
            if (typeErrorProfile.profile(inliningTarget, getBasesNode.execute(frame, cls) == null)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_TYPE, instance);
            }

            PythonObject instanceClass = getInstanceClassAttr(frame, instance);
            return instanceClass != null && abstractIsSubclassNode.execute(frame, instanceClass, cls);
        }
    }

    @Builtin(name = J___SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!isNativeClass(cls)", "!isNativeClass(derived)"})
        static boolean doManagedManaged(VirtualFrame frame, Object cls, Object derived,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached IsSubtypeNode isSubtypeNode) {
            return isSameTypeNode.execute(inliningTarget, cls, derived) || isSubtypeNode.execute(frame, derived, cls);
        }

        @Specialization
        static boolean doObjectObject(VirtualFrame frame, Object cls, Object derived,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsBuiltinObjectProfile isAttrErrorProfile,
                        @Cached("create(T___BASES__)") GetFixedAttributeNode getBasesAttrNode,
                        @Cached PyTupleCheckNode tupleCheck,
                        @Cached TypeNodes.IsTypeNode isClsTypeNode,
                        @Cached TypeNodes.IsTypeNode isDerivedTypeNode,
                        @Cached PRaiseNode raiseNode) {
            if (isSameTypeNode.execute(inliningTarget, cls, derived)) {
                return true;
            }

            // no profiles required because IsTypeNode profiles already
            if (isClsTypeNode.execute(inliningTarget, cls) && isDerivedTypeNode.execute(inliningTarget, derived)) {
                return isSubtypeNode.execute(frame, derived, cls);
            }
            if (!checkClass(frame, inliningTarget, derived, getBasesAttrNode, tupleCheck, isAttrErrorProfile)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S, "issubclass()", 1, "class");
            }
            if (!checkClass(frame, inliningTarget, cls, getBasesAttrNode, tupleCheck, isAttrErrorProfile)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ISSUBCLASS_MUST_BE_CLASS_OR_TUPLE);
            }
            return false;
        }

        // checks if object has '__bases__' (see CPython 'abstract.c' function
        // 'recursive_issubclass')
        private static boolean checkClass(VirtualFrame frame, Node inliningTarget, Object obj, GetFixedAttributeNode getBasesAttrNode, PyTupleCheckNode tupleCheck,
                        IsBuiltinObjectProfile isAttrErrorProfile) {
            Object basesObj;
            try {
                basesObj = getBasesAttrNode.executeObject(frame, obj);
            } catch (PException e) {
                e.expectAttributeError(inliningTarget, isAttrErrorProfile);
                return false;
            }
            return tupleCheck.execute(inliningTarget, basesObj);
        }
    }

    @Builtin(name = J___SUBCLASSHOOK__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class SubclassHookNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object hook(VirtualFrame frame, Object cls, Object subclass) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___SUBCLASSES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SubclassesNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PList getSubclasses(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = true) GetSubclassesAsArrayNode getSubclassesNode) {
            // TODO: missing: keep track of subclasses
            PythonAbstractClass[] array = getSubclassesNode.execute(inliningTarget, cls);
            Object[] classes = new Object[array.length];
            PythonUtils.arraycopy(array, 0, classes, 0, array.length);
            return PFactory.createList(PythonLanguage.get(inliningTarget), classes);
        }
    }

    @GenerateNodeFactory
    abstract static class AbstractSlotNode extends PythonBinaryBuiltinNode {
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CheckSetSpecialTypeAttrNode extends Node {
        abstract void execute(Node inliningTarget, Object type, Object value, TruffleString name);

        @Specialization
        static void check(Node inliningTarget, Object type, Object value, TruffleString name,
                        @Cached PRaiseNode raiseNode,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            if (PGuards.isKindOfBuiltinClass(type) || (getTypeFlagsNode.execute(type) & TypeFlags.IMMUTABLETYPE) != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, name, type);
            }
            if (value == DescriptorDeleteMarker.INSTANCE) {
                // Sic, it's not immutable, but CPython has this message
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_DELETE_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, name, type);
            }
            auditNode.audit(inliningTarget, "object.__setattr__", type, name, value);
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, //
                    allowsDelete = true /* Delete handled by CheckSetSpecialTypeAttrNode */)
    abstract static class NameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString getNameType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getNameBuiltin(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getName(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.LastIndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            TruffleString tpName = getTpNameNode.readFromObj(cls, PyTypeObject__tp_name);
            int nameLen = codePointLengthNode.execute(tpName, TS_ENCODING);
            int lastDot = indexOfCodePointNode.execute(tpName, '.', nameLen, 0, TS_ENCODING);
            if (lastDot < 0) {
                return tpName;
            }
            return substringNode.execute(tpName, lastDot + 1, nameLen - lastDot - 1, TS_ENCODING, true);
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class SetNameInnerNode extends Node {
            abstract void execute(Node inliningTarget, Object type, TruffleString value);

            @Specialization
            static void set(PythonClass type, TruffleString value) {
                type.setName(value);
            }

            @Specialization
            static void set(PythonAbstractNativeObject type, TruffleString value,
                            @Bind PythonLanguage language,
                            @Cached(inline = false) CStructAccess.WritePointerNode writePointerNode,
                            @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObject,
                            @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                            @Cached(inline = false) TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
                value = switchEncodingNode.execute(value, TruffleString.Encoding.UTF_8);
                byte[] bytes = copyToByteArrayNode.execute(value, TruffleString.Encoding.UTF_8);
                PBytes bytesObject = PFactory.createBytes(language, bytes);
                writePointerNode.writeToObj(type, PyTypeObject__tp_name, PySequenceArrayWrapper.ensureNativeSequence(bytesObject));
                PString pString = PFactory.createString(language, value);
                pString.setUtf8Bytes(bytesObject);
                writeObject.writeToObject(type, PyHeapTypeObject__ht_name, pString);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(VirtualFrame frame, Object cls, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckSetSpecialTypeAttrNode check,
                        @Exclusive @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached SetNameInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            check.execute(inliningTarget, cls, value, T___NAME__);
            TruffleString string;
            try {
                string = castToTruffleStringNode.execute(inliningTarget, value);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_P_S_NOT_P, "string", cls, T___NAME__, value);
            }
            if (indexOfCodePointNode.execute(string, 0, 0, codePointLengthNode.execute(string, TS_ENCODING), TS_ENCODING) >= 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
            }
            if (!isValidNode.execute(string, TS_ENCODING)) {
                throw constructAndRaiseNode.get(inliningTarget).raiseUnicodeEncodeError(frame, "utf-8", string, 0, string.codePointLengthUncached(TS_ENCODING), "can't encode classname");
            }
            innerNode.execute(inliningTarget, cls, string);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ModuleNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getModuleType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            TruffleString module = cls.getModuleName();
            return module == null ? T_BUILTINS : module;
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getModuleBuiltin(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            return getModuleType(cls.getType(), value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadAttributeFromObjectNode readAttrNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object module = readAttrNode.execute(cls, T___MODULE__);
            if (module == NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError);
            }
            return module;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModule(PythonClass cls, Object value,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(cls, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttr,
                        @Shared @Cached GetTypeFlagsNode getFlags,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            // see function 'typeobject.c: type_module'
            if ((getFlags.execute(cls) & TypeFlags.HEAPTYPE) != 0) {
                Object module = readAttr.execute(cls, T___MODULE__);
                if (module == NO_VALUE) {
                    throw raiseNode.raise(inliningTarget, AttributeError);
                }
                return module;
            } else {
                // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
                TruffleString tpName = getTpNameNode.readFromObj(cls, PyTypeObject__tp_name);
                int len = codePointLengthNode.execute(tpName, TS_ENCODING);
                int firstDot = indexOfCodePointNode.execute(tpName, '.', 0, len, TS_ENCODING);
                if (firstDot < 0) {
                    return T_BUILTINS;
                }
                return substringNode.execute(tpName, 0, firstDot, TS_ENCODING, true);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setNative(PythonAbstractNativeObject cls, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetTypeFlagsNode getFlags,
                        @Cached("createForceType()") WriteAttributeToObjectNode writeAttr,
                        @Shared @Cached PRaiseNode raiseNode) {
            long flags = getFlags.execute(cls);
            if ((flags & TypeFlags.HEAPTYPE) == 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_N_S, cls, T___MODULE__);
            }
            writeAttr.execute(cls, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModuleType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModuleBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, //
                    allowsDelete = true /* Delete handled by CheckSetSpecialTypeAttrNode */)
    abstract static class QualNameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString getName(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getQualName();
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeFlagsNode getTypeFlagsNode,
                        @Cached CStructAccess.ReadObjectNode getHtName,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            if ((getTypeFlagsNode.execute(cls) & TypeFlags.HEAPTYPE) != 0) {
                return getHtName.readFromObj(cls, PyHeapTypeObject__ht_qualname);
            } else {
                // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
                TruffleString tpName = getTpNameNode.readFromObj(cls, PyTypeObject__tp_name);
                int nameLen = codePointLengthNode.execute(tpName, TS_ENCODING);
                int firstDot = indexOfCodePointNode.execute(tpName, '.', 0, nameLen, TS_ENCODING);
                if (firstDot < 0) {
                    return tpName;
                }
                return substringNode.execute(tpName, firstDot + 1, nameLen - firstDot - 1, TS_ENCODING, true);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class SetQualNameInnerNode extends Node {
            abstract void execute(Node inliningTarget, Object type, TruffleString value);

            @Specialization
            static void set(PythonClass type, TruffleString value) {
                type.setQualName(value);
            }

            @Specialization
            static void set(PythonAbstractNativeObject type, TruffleString value,
                            @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObject) {
                writeObject.writeToObject(type, PyHeapTypeObject__ht_qualname, value);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(Object cls, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckSetSpecialTypeAttrNode check,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached SetQualNameInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            check.execute(inliningTarget, cls, value, T___QUALNAME__);
            TruffleString stringValue;
            try {
                stringValue = castToStringNode.execute(inliningTarget, value);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_STR_TO_QUALNAME, cls, value);
            }
            innerNode.execute(inliningTarget, cls, stringValue);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICTOFFSET__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictoffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getDictoffsetType(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetDictOffsetNode getDictOffsetNode) {
            return getDictOffsetNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___ITEMSIZE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ItemsizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static long getItemsizeType(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetItemSizeNode getItemsizeNode) {
            return getItemsizeNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___BASICSIZE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BasicsizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getBasicsizeType(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetBasicSizeNode getBasicSizeNode) {
            return getBasicSizeNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___WEAKREFOFFSET__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class WeakrefOffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetWeakListOffsetNode getWeakListOffsetNode) {
            return getWeakListOffsetNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___FLAGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FlagsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached GetTypeFlagsNode getTypeFlagsNode,
                        @Cached PRaiseNode raiseNode) {
            if (PGuards.isClass(inliningTarget, self, isTypeNode)) {
                return getTypeFlagsNode.execute(self);
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.DESC_FLAG_FOR_TYPE_DOESNT_APPLY_TO_OBJ, self);
        }
    }

    @Builtin(name = J___ABSTRACTMETHODS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AbstractMethodsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(Object self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            // Avoid returning this descriptor
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                Object result = readAttributeFromObjectNode.execute(self, T___ABSTRACTMETHODS__);
                if (result != NO_VALUE) {
                    return result;
                }
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.OBJ_N_HAS_NO_ATTR_S, self, T___ABSTRACTMETHODS__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        static Object set(VirtualFrame frame, PythonClass self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                writeAttributeToObjectNode.execute(self, T___ABSTRACTMETHODS__, value);
                self.setAbstractClass(isTrueNode.execute(frame, value));
                return PNone.NONE;
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object delete(PythonClass self, @SuppressWarnings("unused") DescriptorDeleteMarker value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Exclusive @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                if (readAttributeFromObjectNode.execute(self, T___ABSTRACTMETHODS__) != NO_VALUE) {
                    writeAttributeToObjectNode.execute(self, T___ABSTRACTMETHODS__, NO_VALUE);
                    self.setAbstractClass(false);
                    return PNone.NONE;
                }
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object set(Object self, Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1, doc = "__dir__ for type objects\n\n\tThis includes all attributes of klass and all of the base\n\tclasses recursively.")
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Override
        public abstract PSet execute(VirtualFrame frame, Object klass);

        @Specialization
        static PSet dir(VirtualFrame frame, Object klass,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached("createGetAttrNode()") GetFixedAttributeNode getBasesNode) {
            return dir(frame, inliningTarget, klass, lookupAttrNode, callNode, getBasesNode, toArrayNode);
        }

        private static PSet dir(VirtualFrame frame, Node inliningTarget, Object klass, PyObjectLookupAttr lookupAttrNode, com.oracle.graal.python.nodes.call.CallNode callNode,
                        GetFixedAttributeNode getBasesNode, ToArrayNode toArrayNode) {
            PSet names = PFactory.createSet(PythonLanguage.get(inliningTarget));
            Object updateCallable = lookupAttrNode.execute(frame, inliningTarget, names, T_UPDATE);
            Object ns = lookupAttrNode.execute(frame, inliningTarget, klass, T___DICT__);
            if (ns != NO_VALUE) {
                callNode.execute(frame, updateCallable, ns);
            }
            Object basesAttr = getBasesNode.execute(frame, klass);
            if (basesAttr instanceof PTuple) {
                Object[] bases = toArrayNode.execute(inliningTarget, ((PTuple) basesAttr).getSequenceStorage());
                for (Object cls : bases) {
                    // Note that since we are only interested in the keys, the order
                    // we merge classes is unimportant
                    Object baseNames = dir(frame, inliningTarget, cls, lookupAttrNode, callNode, getBasesNode, toArrayNode);
                    callNode.execute(frame, updateCallable, baseNames);
                }
            }
            return names;
        }

        @NeverDefault
        protected GetFixedAttributeNode createGetAttrNode() {
            return GetFixedAttributeNode.create(T___BASES__);
        }

        @NeverDefault
        public static DirNode create() {
            return TypeBuiltinsFactory.DirNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        Object union(Object self, Object other,
                        @Cached GenericTypeNodes.UnionTypeOrNode orNode) {
            return orNode.execute(self, other);
        }
    }

    @Builtin(name = J___ANNOTATIONS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AnnotationsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(Object self, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedBranchProfile createDict,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == NO_VALUE) {
                createDict.enter(inliningTarget);
                annotations = PFactory.createDict(PythonLanguage.get(inliningTarget));
                try {
                    write.execute(self, T___ANNOTATIONS__, annotations);
                } catch (PException e) {
                    throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, T___ANNOTATIONS__);
                }
            }
            return annotations;
        }

        @Specialization(guards = "isDeleteMarker(value)")
        static Object delete(Object self, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            try {
                write.execute(self, T___ANNOTATIONS__, NO_VALUE);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___ANNOTATIONS__, self);
            }
            if (annotations == NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError, new Object[]{T___ANNOTATIONS__});
            }
            return PNone.NONE;
        }

        @Fallback
        static Object set(Object self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                write.execute(self, T___ANNOTATIONS__, value);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___ANNOTATIONS__, self);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TextSignatureNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object signature(Object type) {
            if (!(type instanceof PythonBuiltinClassType || type instanceof PythonBuiltinClass)) {
                return PNone.NONE;
            }
            TpSlots slots = GetTpSlotsNode.executeUncached(type);
            /* Best effort at getting at least something */
            Object newSlot = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.New).execute(type);
            if (!TypeNodes.CheckCallableIsSpecificBuiltinNode.executeUncached(newSlot, BuiltinConstructorsFactory.ObjectNodeFactory.getInstance())) {
                return fromMethod(LookupAttributeInMRONode.Dynamic.getUncached().execute(type, T___NEW__));
            }
            if (slots.tp_init() instanceof TpSlotInitBuiltin<?> builtin && builtin != ObjectBuiltins.SLOTS.tp_init()) {
                return AbstractFunctionBuiltins.TextSignatureNode.signatureToText(builtin.getSignature(), true);
            }
            // object() signature
            return StringLiterals.T_EMPTY_PARENS;
        }

        private static Object fromMethod(Object method) {
            if (method instanceof PBuiltinFunction || method instanceof PBuiltinMethod || method instanceof PFunction || method instanceof PMethod) {
                Signature signature = FunctionNodes.GetSignatureNode.executeUncached(method);
                return AbstractFunctionBuiltins.TextSignatureNode.signatureToText(signature, true);
            }
            return PNone.NONE;
        }
    }
}
