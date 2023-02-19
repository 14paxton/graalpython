/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CharPtrToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.FinishArgNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToNativeBorrowedNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToNativeTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToPythonStringNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToPythonWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.WrappedPointerToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckInquiryResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckIterNextResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckPrimitiveFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.InitCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ToInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ToInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.util.Supplier;

enum ArgBehavior {
    PyObject("POINTER", "J", "jlong", "long", ToNativeNode::new, ToPythonNode::new, ToNativeTransferNode::new, ToPythonTransferNode::new, null),
    PyObjectBorrowed("POINTER", "J", "jlong", "long", ToNativeBorrowedNode::new, ToPythonNode::new, null, null, null),
    PyObjectAsTruffleString("POINTER", "J", "jlong", "long", null, ToPythonStringNode::new, null, null, null),
    PyObjectWrapper("POINTER", "J", "jlong", "long", null, ToPythonWrapperNode::new, null, null, null),
    Pointer("POINTER", "J", "jlong", "long", null, null, null),
    WrappedPointer("POINTER", "J", "jlong", "long", null, WrappedPointerToPythonNode::new, null),
    TruffleStringPointer("STRING", "J", "jlong", "long", null, CharPtrToJavaNodeGen::create, null),
    JavaStringPointer("STRING", "J", "jlong", "long", null, null, null),
    Char8("SINT8", "C", "jbyte", "byte", null, null, null),
    Char16("SINT16", "C", "jchar", "char", null, null, null),
    Int32("SINT32", "I", "jint", "int", ToInt32NodeGen::create, null, null),
    Int64("SINT64", "J", "jlong", "long", ToInt64NodeGen::create, null, null),
    Float64("DOUBLE", "D", "jdouble", "double", null, null, null),
    Void("VOID", "V", "void", "void", null, null, null),
    Unknown("SINT64", "J", "jlong", "long", null, null, null),
    ;

    public final String nfiSignature;
    public final String jniSignature;
    public final String jniType;
    public final String javaSignature;
    public final Supplier<CExtToNativeNode> pythonToNative;
    public final Supplier<CExtToJavaNode> nativeToPython;
    public final Supplier<CExtToNativeNode> pythonToNativeTransfer;
    public final Supplier<CExtToJavaNode> nativeToPythonTransfer;
    public final Supplier<FinishArgNode> finish;

    ArgBehavior(String nfiSignature, String jniSignature, String jniType, String javaSignature, Supplier<CExtToNativeNode> pythonToNative, Supplier<CExtToJavaNode> nativeToPython,
                    Supplier<CExtToNativeNode> pythonToNativeTransfer, Supplier<CExtToJavaNode> nativeToPythonTransfer, Supplier<FinishArgNode> finish) {
        this.nfiSignature = nfiSignature;
        this.jniSignature = jniSignature;
        this.jniType = jniType;
        this.javaSignature = javaSignature;
        this.pythonToNative = pythonToNative;
        this.nativeToPython = nativeToPython;
        this.pythonToNativeTransfer = pythonToNativeTransfer;
        this.nativeToPythonTransfer = nativeToPythonTransfer;
        this.finish = finish;
    }

    ArgBehavior(String nfiSignature, String jniSignature, String jniType, String javaType, Supplier<CExtToNativeNode> pythonToNative, Supplier<CExtToJavaNode> nativeToPython,
                    Supplier<FinishArgNode> finish) {
        this(nfiSignature, jniSignature, jniType, javaType, pythonToNative, nativeToPython, null, null, finish);
    }
}

public enum ArgDescriptor {
    Void(ArgBehavior.Void, "void"),
    VoidNoReturn(ArgBehavior.Void, "void"),
    PyObject(ArgBehavior.PyObject, "PyObject*"),
    ConstPyObject(ArgBehavior.PyObject, "const PyObject*"),
    PyObjectBorrowed(ArgBehavior.PyObjectBorrowed, "PyObject*"),
    PyObjectWrapper(ArgBehavior.PyObjectWrapper, "PyObject*"),
    PyObjectAsTruffleString(ArgBehavior.PyObjectAsTruffleString, "PyObject*"),
    PyTypeObject(ArgBehavior.PyObject, "PyTypeObject*"),
    PyTypeObjectTransfer(ArgBehavior.PyObject, "PyTypeObject*", true),
    PyListObject(ArgBehavior.PyObject, "PyListObject*"),
    PyTupleObject(ArgBehavior.PyObject, "PyTupleObject*"),
    PyMethodObject(ArgBehavior.PyObject, "PyMethodObject*"),
    PyInstanceMethodObject(ArgBehavior.PyObject, "PyInstanceMethodObject*"),
    PyObjectTransfer(ArgBehavior.PyObject, "PyObject*", true),
    Pointer(ArgBehavior.Pointer, "void*"),
    Py_ssize_t(ArgBehavior.Int64, "Py_ssize_t"),
    Py_hash_t(ArgBehavior.Int64, "Py_hash_t"),
    Int(ArgBehavior.Int32, "int"),
    ConstInt(ArgBehavior.Int32, "const int"),
    Double(ArgBehavior.Float64, "double"),
    Long(ArgBehavior.Int64, "long"),

    _FRAME(ArgBehavior.PyObject, "struct _frame*"),
    _MOD_PTR("struct _mod*"),
    _NODE_PTR("struct _node*"),
    _PY_CLOCK_INFO_T_PTR("_Py_clock_info_t*"),
    _PY_ERROR_HANDLER("_Py_error_handler"),
    _PY_IDENTIFIER_PTR("struct _Py_Identifier*"),
    _PYARG_PARSER_PTR("struct _PyArg_Parser*"),
    _PYBYTESWRITER_PTR("_PyBytesWriter*"),
    _PYCROSSINTERPRETERDATA_PTR("_PyCrossInterpreterData*"),
    _PYERR_STACKITEM_PTR("_PyErr_StackItem*"),
    _PYTIME_ROUND_T("_PyTime_round_t"),
    _PYTIME_T("_PyTime_t"),
    _PYTIME_T_PTR("_PyTime_t*"),
    _PYUNICODEWRITER_PTR("_PyUnicodeWriter*"),
    CHAR(ArgBehavior.Char8, "char"),
    CHAR_CONST("char*const"),
    CHAR_CONST_PTR("char*const*"),
    CHAR_CONST_ARRAY("char*const []"),
    CHAR_PTR(ArgBehavior.Pointer, "char*"),
    CHAR_PTR_LIST(ArgBehavior.Pointer, "char**"),
    ConstCharPtrAsTruffleString(ArgBehavior.TruffleStringPointer, "const char*"),
    ConstCharPtr(ArgBehavior.Pointer, "const char*"),
    CharPtrAsTruffleString(ArgBehavior.TruffleStringPointer, "char*"),
    CONST_CHAR_PTR_LIST("const char**"),
    CONST_PY_BUFFER("const Py_buffer*"),
    CONST_PY_SSIZE_T("const Py_ssize_t"),
    CONST_PY_SSIZE_T_PTR("const Py_ssize_t*"),
    CONST_PY_UCS4("const Py_UCS4"),
    CONST_PY_UNICODE("const Py_UNICODE*"),
    CONST_PYCONFIG_PTR("const PyConfig*"),
    CONST_PYPRECONFIG_PTR("const PyPreConfig*"),
    CONST_UNSIGNED_CHAR_PTR("const unsigned char*"),
    CONST_VOID_PTR("const void*"),
    CONST_VOID_PTR_LIST("const void**"),
    CONST_WCHAR_PTR("const wchar_t*"),
    CROSSINTERPDATAFUNC("crossinterpdatafunc"),
    FILE_PTR("FILE*"),
    FREEFUNC("freefunc"),
    INITTAB("struct _inittab*"),
    INT_LIST("int*"),
    INT8_T_PTR(ArgBehavior.Pointer, "int8_t*"),
    INT64_T(ArgBehavior.Int64, "int64_t"),
    LONG_LONG(ArgBehavior.Int64, "long long"),
    LONG_PTR("long*"),
    PyASCIIObject(ArgBehavior.PyObject, "PyASCIIObject*"),
    PY_AUDITHOOKFUNCTION("Py_AuditHookFunction"),
    PY_BUFFER("Py_buffer*"),
    PY_C_FUNCTION("PyCFunction"),
    PyByteArrayObject(ArgBehavior.PyObject, "PyByteArrayObject*"),
    PyCFunctionObject(ArgBehavior.PyObject, "PyCFunctionObject*"),
    PyCMethodObject(ArgBehavior.PyObject, "PyCMethodObject*"),
    PY_CAPSULE_DESTRUCTOR(ArgBehavior.Pointer, "PyCapsule_Destructor"),
    PyCodeObject(ArgBehavior.PyObject, "PyCodeObject*"),
    PyCodeObjectTransfer(ArgBehavior.PyObject, "PyCodeObject*", true),
    PY_COMPILER_FLAGS(ArgBehavior.Pointer, "PyCompilerFlags*"),
    PY_COMPLEX("Py_complex"),
    PyCodeAddressRange("PyCodeAddressRange*"),
    PyCompactUnicodeObject(ArgBehavior.PyObject, "PyCompactUnicodeObject*"),
    PyFrameConstructor("PyFrameConstructor*"),
    PyFrameObject(ArgBehavior.PyObject, "PyFrameObject*"),
    PyFrameObjectTransfer(ArgBehavior.PyObject, "PyFrameObject*", true),
    _PyFrameEvalFunction("_PyFrameEvalFunction"),
    PY_GEN_OBJECT(ArgBehavior.PyObject, "PyGenObject*"),
    PyGetSetDef(ArgBehavior.Pointer, "PyGetSetDef*"),
    PY_GIL_STATE_STATE(ArgBehavior.Int32, "PyGILState_STATE"),
    PY_HASH_T_PTR("Py_hash_t*"),
    PY_IDENTIFIER("_Py_Identifier*"),
    PyInterpreterState(ArgBehavior.Pointer, "PyInterpreterState*"),
    PY_LOCK_STATUS("PyLockStatus"),
    PyLongObject(ArgBehavior.PyObject, "PyLongObject*"),
    PyMemberDef(ArgBehavior.Pointer, "struct PyMemberDef*"),
    PyModuleObject(ArgBehavior.PyObject, "PyModuleObject*"),
    PyModuleObjectTransfer(ArgBehavior.PyObject, "PyModuleObject*", true),
    PyMethodDef(ArgBehavior.WrappedPointer, "PyMethodDef*"),
    PyModuleDef(ArgBehavior.Pointer, "PyModuleDef*"), // it's unclear if this should be PyObject
    PyNumberMethods(ArgBehavior.Pointer, "PyNumberMethods*"),
    PySequenceMethods(ArgBehavior.Pointer, "PySequenceMethods*"),
    PyMappingMethods(ArgBehavior.Pointer, "PyMappingMethods*"),
    PyAsyncMethods(ArgBehavior.Pointer, "PyAsyncMethods*"),
    PyBufferProcs(ArgBehavior.Pointer, "PyBufferProcs*"),
    PyMethodDescrObject(ArgBehavior.PyObject, "PyMethodDescrObject*"),
    PySendResult(ArgBehavior.Int32, "PySendResult"),
    PySetObject(ArgBehavior.PyObject, "PySetObject*"),
    PyDescrObject(ArgBehavior.PyObject, "PyDescrObject*"),
    PY_OPENCODEHOOKFUNCTION("Py_OpenCodeHookFunction"),
    PY_OS_SIGHANDLER("PyOS_sighandler_t"),
    PySliceObject(ArgBehavior.PyObject, "PySliceObject*"),
    PY_SSIZE_T_PTR("Py_ssize_t*"),
    PY_STRUCT_SEQUENCE_DESC("PyStructSequence_Desc*"),
    PyThreadState(ArgBehavior.Pointer, "PyThreadState*"),
    PY_THREAD_TYPE_LOCK(ArgBehavior.Int64, "PyThread_type_lock"),
    PY_THREAD_TYPE_LOCK_PTR(ArgBehavior.Pointer, "PyThread_type_lock*"),
    PyTryBlock("PyTryBlock*"),
    PY_TRACEFUNC("Py_tracefunc"),
    PY_TSS_T_PTR("Py_tss_t*"),
    TS_PTR("struct _ts*"),
    PY_TYPE_SPEC("PyType_Spec*"),
    PY_UCS4(ArgBehavior.Int32, "Py_UCS4"),
    PY_UCS4_PTR("Py_UCS4*"),
    PY_UNICODE("Py_UNICODE"),
    PyUnicodeObject(ArgBehavior.PyObject, "PyUnicodeObject*"),
    PY_UNICODE_PTR("Py_UNICODE*"),
    PyVarObject(ArgBehavior.PyObject, "PyVarObject*"),
    ConstPyVarObject(ArgBehavior.PyObject, "const PyVarObject*"),
    PYADDRPAIR_PTR("PyAddrPair*"),
    PYCONFIG_PTR("PyConfig*"),
    PYDICTKEYSOBJECT_PTR("PyDictKeysObject*"),
    PYDICTOBJECT_PTR("PyDictObject*"),
    PYHASH_FUNCDEF_PTR("PyHash_FuncDef*"),
    PYMEMALLOCATORDOMAIN("PyMemAllocatorDomain"),
    PYMEMALLOCATOREX_PTR("PyMemAllocatorEx*"),
    PYMODULEDEF_PTR("struct PyModuleDef*"),
    PyObjectConst("PyObject*const"),
    PyObjectConstPtr("PyObject*const*"),
    PYOBJECT_CONST_PTR_LIST("PyObject*const**"),
    PyObjectPtr(ArgBehavior.Pointer, "PyObject**"),
    PYOBJECTARENAALLOCATOR_PTR("PyObjectArenaAllocator*"),
    PYPRECONFIG_PTR("PyPreConfig*"),
    PYSTATUS("PyStatus"),
    PYUNICODE_KIND("enum PyUnicode_Kind"),
    PYWEAKREFERENCE_PTR("PyWeakReference*"),
    PYWIDESTRINGLIST_PTR("PyWideStringList*"),
    SIZE_T(ArgBehavior.Int64, "size_t"),
    SIZE_T_PTR("size_t*"),
    STAT_PTR("struct stat*"),
    TIME_T("time_t"),
    TIME_T_PTR("time_t*"),
    TIMESPEC_PTR("struct timespec*"),
    TIMEVAL_PTR("struct timeval*"),
    TM_PTR("struct tm*"),
    UINTPTR_T(ArgBehavior.Pointer, "uintptr_t"),
    UINT64_T(ArgBehavior.Int64, "uint64_t"),
    UNSIGNED_CHAR_PTR("unsigned char*"),
    UNSIGNED_INT(ArgBehavior.Int32, "unsigned int"),
    UNSIGNED_LONG(ArgBehavior.Int64, "unsigned long"),
    UNSIGNED_LONG_LONG(ArgBehavior.Int64, "unsigned long long"),
    VA_LIST(ArgBehavior.Pointer, "va_list"),
    VA_LIST_PTR(ArgBehavior.Pointer, "va_list*"),
    VARARGS("..."),
    VOID_PTR_LIST("void**"),
    WCHAR_T_PTR(ArgBehavior.Pointer, "wchar_t*"),
    WCHAR_T_CONST_PTR(ArgBehavior.Pointer, "wchar_t*const*"),
    WCHAR_T_PTR_LIST(ArgBehavior.Pointer, "wchar_t**"),
    WCHAR_T_PTR_PTR_LIST(ArgBehavior.Pointer, "wchar_t***"),
    WRAPPERBASE(ArgBehavior.Pointer, "struct wrapperbase*"),
    destructor(ArgBehavior.Pointer, "destructor"),
    getattrfunc(ArgBehavior.Pointer, "getattrfunc"),
    setattrfunc(ArgBehavior.Pointer, "setattrfunc"),
    reprfunc(ArgBehavior.Pointer, "reprfunc"),
    hashfunc(ArgBehavior.Pointer, "hashfunc"),
    ternaryfunc(ArgBehavior.Pointer, "ternaryfunc"),
    getattrofunc(ArgBehavior.Pointer, "getattrofunc"),
    setattrofunc(ArgBehavior.Pointer, "setattrofunc"),
    traverseproc(ArgBehavior.Pointer, "traverseproc"),
    inquiry(ArgBehavior.Pointer, "inquiry"),
    richcmpfunc(ArgBehavior.Pointer, "richcmpfunc"),
    getiterfunc(ArgBehavior.Pointer, "getiterfunc"),
    iternextfunc(ArgBehavior.Pointer, "iternextfunc"),
    descrgetfunc(ArgBehavior.Pointer, "descrgetfunc"),
    descrsetfunc(ArgBehavior.Pointer, "descrsetfunc"),
    initproc(ArgBehavior.Pointer, "initproc"),
    allocfunc(ArgBehavior.Pointer, "allocfunc"),
    newfunc(ArgBehavior.Pointer, "newfunc"),
    freefunc(ArgBehavior.Pointer, "freefunc"),
    vectorcallfunc(ArgBehavior.Pointer, "vectorcallfunc"),
    binaryfunc(ArgBehavior.Pointer, "binaryfunc"),
    unaryfunc(ArgBehavior.Pointer, "unaryfunc"),
    lenfunc(ArgBehavior.Pointer, "lenfunc"),
    ssizeargfunc(ArgBehavior.Pointer, "ssizeargfunc"),
    ssizeobjargproc(ArgBehavior.Pointer, "ssizeobjargproc"),
    objobjproc(ArgBehavior.Pointer, "objobjproc"),
    objobjargproc(ArgBehavior.Pointer, "objobjargproc"),
    getbufferproc(ArgBehavior.Pointer, "getbufferproc"),
    releasebufferproc(ArgBehavior.Pointer, "releasebufferproc"),
    getter(ArgBehavior.Pointer, "getter"),
    setter(ArgBehavior.Pointer, "setter"),
    mmap_object(ArgBehavior.PyObject, "mmap_object*"),

    func_objint("int (*)(PyObject*value)"),
    func_voidvoidptr("void (*)(void*)"),
    func_voidvoid("void (*)(void)"),
    func_intvoidptr("int (*)(void*)"),
    func_objvoid("PyObject*(*)(void)"),
    func_objcharsizevoidptr("PyObject*(*)(const char*, Py_ssize_t, void*)"),

    IterResult(ArgBehavior.PyObject, "void*", CheckIterNextResultNodeGen::create),
    InquiryResult(ArgBehavior.Int32, "int", CheckInquiryResultNodeGen::create),
    InitResult(ArgBehavior.Int32, "int", InitCheckFunctionResultNodeGen::create),
    PrimitiveResult32(ArgBehavior.Int32, "int", CheckPrimitiveFunctionResultNodeGen::create),
    PrimitiveResult64(ArgBehavior.Int64, "long", CheckPrimitiveFunctionResultNodeGen::create),
    ;

    public final String cSignature;
    public final ArgBehavior behavior;
    private final boolean transfer;
    private final Supplier<CheckFunctionResultNode> checkResult;

    ArgDescriptor(String cSignature) {
        this.behavior = ArgBehavior.Unknown;
        this.cSignature = cSignature;
        this.transfer = false;
        this.checkResult = null;
    }

    ArgDescriptor(ArgBehavior behavior, String cSignature) {
        this.behavior = behavior;
        this.cSignature = cSignature;
        this.transfer = false;
        this.checkResult = null;
    }

    ArgDescriptor(ArgBehavior behavior, String cSignature, boolean transfer) {
        this.behavior = behavior;
        this.cSignature = cSignature;
        this.transfer = transfer;
        this.checkResult = null;
    }

    ArgDescriptor(ArgBehavior behavior, String cSignature, Supplier<CheckFunctionResultNode> checkResult) {
        this.behavior = behavior;
        this.cSignature = cSignature;
        this.checkResult = checkResult;
        this.transfer = false;
    }

    public static CExtToJavaNode[] createNativeToPython(ArgDescriptor[] args) {
        CExtToJavaNode[] result = new CExtToJavaNode[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = args[i].createNativeToPythonNode();
        }
        return result;
    }

    public String getCSignature() {
        return cSignature;
    }

    public CExtToNativeNode createPythonToNativeNode() {
        assert behavior != ArgBehavior.Unknown : "undefined behavior in " + this;
        Supplier<CExtToNativeNode> factory = transfer ? behavior.pythonToNativeTransfer : behavior.pythonToNative;
        assert !(transfer && factory == null);
        return factory == null ? null : factory.get();
    }

    public CExtToJavaNode createNativeToPythonNode() {
        assert behavior != ArgBehavior.Unknown : "undefined behavior in " + this;
        Supplier<CExtToJavaNode> factory = transfer ? behavior.nativeToPythonTransfer : behavior.nativeToPython;
        assert !(transfer && factory == null);
        return factory == null ? null : factory.get();
    }

    public CheckFunctionResultNode createCheckResultNode() {
        assert behavior != ArgBehavior.Unknown : "undefined behavior in " + this;
        return checkResult == null ? null : checkResult.get();
    }

    public String getNFISignature() {
        return behavior.nfiSignature;
    }

    public String getJniSignature() {
        return behavior.jniSignature;
    }

    public String getJniType() {
        return behavior.jniType;
    }

    public String getJavaSignature() {
        return behavior.javaSignature;
    }

    public boolean isPyObjectOrPointer() {
        return behavior == ArgBehavior.PyObject || behavior == ArgBehavior.PyObjectBorrowed || behavior == ArgBehavior.Pointer || behavior == ArgBehavior.WrappedPointer ||
                        behavior == ArgBehavior.TruffleStringPointer ||
                        behavior == ArgBehavior.JavaStringPointer;
    }

    public boolean isIntType() {
        return behavior == ArgBehavior.Int32 || behavior == ArgBehavior.Int64 || behavior == ArgBehavior.Char16 || behavior == ArgBehavior.Unknown;
    }

    public boolean isFloatType() {
        return behavior == ArgBehavior.Float64;
    }

    public boolean isVoid() {
        return behavior == ArgBehavior.Void;
    }

    public static ArgDescriptor resolve(String sig) {
        switch (sig) {
            case "struct _typeobject*":
                return PyTypeObject;
            case "struct PyGetSetDef*":
                return PyGetSetDef;
            case "const struct PyConfig*":
                return CONST_PYCONFIG_PTR;
            case "struct PyConfig*":
                return PYCONFIG_PTR;
        }
        for (ArgDescriptor d : values()) {
            if (d.cSignature.equals(sig)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown C signature: " + sig);
    }
}
