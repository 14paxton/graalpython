/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.METH_CLASS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi7BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi8BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CreateFunctionNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PyObjectSetAttrNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.CreateFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.ReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.WriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CharPtrToJavaObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.Store;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class PythonCextTypeBuiltins {

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyTypeObject, PyObject}, call = Direct)
    abstract static class _PyType_Lookup extends CApiBinaryBuiltinNode {
        @Specialization
        Object doGeneric(Object type, Object name,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttributeInMRONode) {
            Object result = lookupAttributeInMRONode.execute(type, name);
            if (result == PNone.NO_VALUE) {
                return getNativeNull();
            }
            return result;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyTypeObject}, call = Direct, inlined = true)
    @ImportStatic(PythonOptions.class)
    abstract static class PyType_IsSubtype extends CApiBinaryBuiltinNode {

        @Specialization
        static int doGeneric(Object a, Object b,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return PInt.intValue(isSubtypeNode.execute(a, b));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    public abstract static class PyTruffle_Compute_Mro extends CApiBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doIt(PythonNativeClass self, TruffleString className) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(self);
            return factory().createTuple(new MroSequenceStorage(className, doSlowPath));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject}, call = Ignored)
    public abstract static class PyTruffle_NewTypeDict extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDict doGeneric(PythonNativeClass nativeClass) {
            PythonLanguage language = PythonLanguage.get(null);
            Store nativeTypeStore = new Store(language.getEmptyShape());
            DynamicObjectLibrary.getUncached().put(nativeTypeStore, PythonNativeClass.INSTANCESHAPE, language.getShapeForClass(nativeClass));
            return PythonObjectFactory.getUncached().createDict(new DynamicObjectStorage(nativeTypeStore));
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, ConstCharPtrAsTruffleString, PyObject}, call = Ignored)
    public abstract static class PyTruffle_Type_Modified extends CApiTernaryBuiltinNode {

        @TruffleBoundary
        @Specialization(guards = "isNoValue(mroTuple)")
        int doIt(PythonNativeClass clazz, TruffleString name, @SuppressWarnings("unused") PNone mroTuple) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption(clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name.toJavaStringUncached() + "\") (without MRO) called");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return 0;
        }

        @TruffleBoundary
        @Specialization
        int doIt(PythonNativeClass clazz, TruffleString name, PTuple mroTuple,
                        @Cached("createClassProfile()") ValueProfile profile) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption(clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name.toJavaStringUncached() + "\") called");
            }
            SequenceStorage sequenceStorage = profile.profile(mroTuple.getSequenceStorage());
            if (sequenceStorage instanceof MroSequenceStorage) {
                ((MroSequenceStorage) sequenceStorage).lookupChanged();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid MRO object for native type \"" + name.toJavaStringUncached() + "\"");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, Pointer}, call = Ignored)
    abstract static class PyTruffle_Trace_Type extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffle_Trace_Type.class);

        @Specialization(limit = "3")
        int trace(Object ptr, Object classNameObj,
                        @CachedLibrary("ptr") InteropLibrary ptrLib,
                        @CachedLibrary("classNameObj") InteropLibrary nameLib,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            final TruffleString className;
            if (nameLib.isString(classNameObj)) {
                try {
                    className = switchEncodingNode.execute(nameLib.asTruffleString(classNameObj), TS_ENCODING);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException();
                }
            } else {
                className = null;
            }
            PythonContext context = getContext();
            Object primitivePtr = CApiContext.asPointer(ptr, ptrLib);
            context.getCApiContext().traceStaticMemory(primitivePtr, null, className);
            LOGGER.fine(() -> PythonUtils.formatJString("Initializing native type %s (ptr = %s)", className, CApiContext.asHex(primitivePtr)));
            return 0;
        }
    }

    @ImportStatic(CExtContext.class)
    abstract static class NewClassMethodNode extends Node {

        abstract Object execute(Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object type, Object doc,
                        PythonObjectFactory factory);

        @Specialization(guards = "isClassOrStaticMethod(flags)")
        static Object classOrStatic(Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object type,
                        Object doc, PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Shared("cf") @Cached CreateFunctionNode createFunctionNode,
                        @Shared("cstr") @Cached CharPtrToJavaObjectNode cstrPtr) {
            Object func = createFunctionNode.execute(name, methObj, wrapper, type, flags, factory);
            PythonObject function;
            if ((flags & METH_CLASS) != 0) {
                function = factory.createClassmethodFromCallableObj(func);
            } else {
                function = factory.createStaticmethodFromCallableObj(func);
            }
            dylib.put(function, T___NAME__, name);
            dylib.put(function, T___DOC__, cstrPtr.execute(doc));
            dylib.put(function, PythonCextMethodBuiltins.METHOD_DEF_PTR, methodDefPtr);
            return function;
        }

        @Specialization(guards = "!isClassOrStaticMethod(flags)")
        static Object doNativeCallable(Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object type,
                        Object doc, PythonObjectFactory factory,
                        @Cached PyObjectSetAttrNode setattr,
                        @Cached WriteAttributeToObjectNode write,
                        @Shared("cf") @Cached CreateFunctionNode createFunctionNode,
                        @Shared("cstr") @Cached CharPtrToJavaObjectNode cstrPtr) {
            Object func = createFunctionNode.execute(name, methObj, wrapper, type, flags, factory);
            setattr.execute(func, T___NAME__, name);
            setattr.execute(func, T___DOC__, cstrPtr.execute(doc));
            write.execute(func, PythonCextMethodBuiltins.METHOD_DEF_PTR, methodDefPtr);
            return func;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, call = Ignored)
    abstract static class PyTruffleType_AddFunctionToType extends CApi8BuiltinNode {

        @Specialization
        int classMethod(Object methodDefPtr, Object type, Object dict, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached DictBuiltins.SetItemNode setItemNode) {
            Object func = newClassMethodNode.execute(methodDefPtr, name, cfunc, flags, wrapper, type, doc, factory());
            setItemNode.execute(null, dict, name, func);
            return 0;
        }
    }

    /**
     * Signature: {@code (primary, tpDict, name", cfunc, flags, wrapper, doc)}
     */
    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, call = Ignored)
    abstract static class PyTruffleType_AddSlot extends CApi7BuiltinNode {

        @Specialization
        @TruffleBoundary
        static int addSlot(Object clazz, PDict tpDict, TruffleString memberName, Object cfunc, int flags, int wrapper, Object docPtr) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached());

            // create wrapper descriptor
            Object wrapperDescriptor = CreateFunctionNodeGen.getUncached().execute(memberName, cfunc, wrapper, clazz, flags, PythonObjectFactory.getUncached());
            WriteAttributeToDynamicObjectNode.getUncached().execute(wrapperDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add wrapper descriptor to tp_dict
            PyDictSetItem.executeUncached(tpDict, memberName, wrapperDescriptor);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Int, Py_ssize_t, Int, Pointer}, call = CApiCallPath.Ignored)
    public abstract static class PyTruffleType_AddMember extends CApi7BuiltinNode {

        @Specialization
        @TruffleBoundary
        public static int addMember(Object clazz, PDict tpDict, TruffleString memberName, int memberType, long offset, int canSet, Object docPtr) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached());
            PythonLanguage language = PythonLanguage.get(null);
            PBuiltinFunction getterObject = ReadMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, (int) offset);

            Object setterObject = null;
            if (canSet != 0) {
                setterObject = WriteMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, (int) offset);
            }

            // create member descriptor
            GetSetDescriptor memberDescriptor = PythonObjectFactory.getUncached().createMemberDescriptor(getterObject, setterObject, memberName, clazz);
            WriteAttributeToDynamicObjectNode.getUncached().execute(memberDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add member descriptor to tp_dict
            PyDictSetItem.executeUncached(tpDict, memberName, memberDescriptor);
            return 0;
        }
    }

    abstract static class CreateGetSetNode extends Node {

        abstract GetSetDescriptor execute(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory);

        @Specialization
        static GetSetDescriptor createGetSet(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary) {
            assert !(doc instanceof CArrayWrapper);
            // note: 'doc' may be NULL; in this case, we would store 'None'
            PBuiltinFunction get = null;
            if (!interopLibrary.isNull(getter)) {
                RootCallTarget getterCT = getterCallTarget(name, language);
                get = factory.createBuiltinFunction(name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(getter, closure), 0, getterCT);
            }

            PBuiltinFunction set = null;
            boolean hasSetter = !interopLibrary.isNull(setter);
            if (hasSetter) {
                RootCallTarget setterCT = setterCallTarget(name, language);
                set = factory.createBuiltinFunction(name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(setter, closure), 0, setterCT);
            }

            // create get-set descriptor
            GetSetDescriptor descriptor = factory.createGetSetDescriptor(get, set, name, cls, hasSetter);
            dylib.put(descriptor.getStorage(), T___DOC__, doc);
            return descriptor;
        }

        @TruffleBoundary
        private static RootCallTarget getterCallTarget(TruffleString name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new GetterRoot(l, name, PExternalFunctionWrapper.GETTER);
            return lang.createCachedCallTarget(rootNodeFunction, GetterRoot.class, PExternalFunctionWrapper.GETTER, name, true);
        }

        @TruffleBoundary
        private static RootCallTarget setterCallTarget(TruffleString name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new SetterRoot(l, name, PExternalFunctionWrapper.SETTER);
            return lang.createCachedCallTarget(rootNodeFunction, SetterRoot.class, PExternalFunctionWrapper.SETTER, name, true);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer, Pointer, Pointer}, call = Ignored)
    abstract static class PyTruffleType_AddGetSet extends CApi7BuiltinNode {

        @Specialization
        int doGeneric(Object cls, PDict dict, TruffleString name, Object getter, Object setter, Object doc, Object closure,
                        @Cached CharPtrToJavaObjectNode fromCharPointerNode,
                        @Cached CreateGetSetNode createGetSetNode,
                        @Cached PyDictSetItem dictSetItem) {
            GetSetDescriptor descr = createGetSetNode.execute(name, cls, getter, setter, fromCharPointerNode.execute(doc), closure, getLanguage(), factory());
            dictSetItem.execute(null, dict, name, descr);
            return 0;
        }
    }
}
