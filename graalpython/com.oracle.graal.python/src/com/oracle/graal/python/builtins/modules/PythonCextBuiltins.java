/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.METH_CLASS;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.isClassOrStaticMethod;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.HASH_MISMATCH;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.CreateFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.AllocInfo;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.ReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.WriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToNativeLongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CextUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CharPtrToJavaObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.DirectUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.MayRaiseErrorResult;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.MayRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ObjectUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ResolveHandleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToBorrowedRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.UnicodeFromFormatNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.VoidPtrToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CastToNativeLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetRefCntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.PRaiseNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ResolveHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.HandleCache;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCacheFactory.ResolveNativeReferenceNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyCFunctionDecorator;
import com.oracle.graal.python.builtins.objects.cext.capi.PyDateTimeCAPIWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyEvalNodes.PyEvalRestoreThread;
import com.oracle.graal.python.builtins.objects.cext.capi.PyEvalNodes.PyEvalSaveThread;
import com.oracle.graal.python.builtins.objects.cext.capi.PyGILStateNodes.PyGILStateEnsure;
import com.oracle.graal.python.builtins.objects.cext.capi.PyGILStateNodes.PyGILStateRelease;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectAlloc;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodesFactory.UnicodeAsWideCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.Charsets;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.UnicodeFromWcharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.Store;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode.SplitFormatStringNode;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.GetCodeCallTargetNode;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.GetCodeSignatureNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.LenNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.DelItemNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ItemsNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.KeysNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.PopNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ValuesNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence.Descriptor;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode.CreateAndCheckArgumentsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetSignatureNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.ShutdownHook;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.utilities.CyclicAssumption;

@CoreFunctions(defineModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public class PythonCextBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT = "python_cext";

    private static final String ERROR_HANDLER = "error_handler";
    public static final String NATIVE_NULL = "native_null";

    private PythonObject errorHandler;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        PythonClass errorHandlerClass = core.factory().createPythonClassAndFixupSlots(core.getLanguage(), PythonBuiltinClassType.PythonClass,
                        "CErrorHandler", new PythonAbstractClass[]{core.lookupType(PythonBuiltinClassType.PythonObject)});
        builtinConstants.put("CErrorHandler", errorHandlerClass);
        errorHandler = core.factory().createPythonObject(errorHandlerClass);
        builtinConstants.put(ERROR_HANDLER, errorHandler);
        builtinConstants.put(NATIVE_NULL, new PythonNativeNull());
        builtinConstants.put("PyEval_SaveThread", new PyEvalSaveThread());
        builtinConstants.put("PyEval_RestoreThread", new PyEvalRestoreThread());
        builtinConstants.put("PyGILState_Ensure", new PyGILStateEnsure());
        builtinConstants.put("PyGILState_Release", new PyGILStateRelease());
    }

    @FunctionalInterface
    public interface TernaryFunction<T1, T2, T3, R> {
        R apply(T1 arg0, T2 arg1, T3 arg2);
    }

    /**
     * Called mostly from Python code to convert arguments into a wrapped representation for
     * consumption in Python or Java.
     */
    @Builtin(name = "to_java", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToJavaObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object object,
                        @Cached AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(object);
        }
    }

    /**
     * Called from Python code to convert arguments into a wrapped representation for consumption in
     * Python.
     */
    @Builtin(name = "voidptr_to_java", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class VoidPtrToJavaObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object object,
                        @Cached VoidPtrToJavaNode voidPtrtoJavaNode) {
            return voidPtrtoJavaNode.execute(object);
        }
    }

    @Builtin(name = "to_java_type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToJavaClassNode extends ToJavaObjectNode {
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @Builtin(name = "to_char_pointer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruffleString_AsString extends NativeBuiltin {

        @Specialization(guards = "isString(str)")
        Object run(Object str,
                        @Cached AsCharPointerNode asCharPointerNode) {
            return asCharPointerNode.execute(str);
        }

        @Fallback
        Object run(VirtualFrame frame, Object o) {
            return raiseNative(frame, PNone.NO_VALUE, PythonErrorType.SystemError, ErrorMessages.CANNOT_CONVERT_OBJ_TO_C_STRING, o, o.getClass().getName());
        }
    }

    @Builtin(name = "Py_ErrorHandler", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class PyErrorHandlerNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(PythonModule cextPython) {
            return ((PythonCextBuiltins) cextPython.getBuiltins()).errorHandler;
        }
    }

    @Builtin(name = "Py_NotImplemented")
    @GenerateNodeFactory
    public abstract static class PyNotImplementedNode extends PythonBuiltinNode {
        @Specialization
        Object run() {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "Py_True")
    @GenerateNodeFactory
    public abstract static class PyTrueNode extends PythonBuiltinNode {
        @Specialization
        Object run() {
            return true;
        }
    }

    @Builtin(name = "Py_False")
    @GenerateNodeFactory
    public abstract static class PyFalseNode extends PythonBuiltinNode {
        @Specialization
        Object run() {
            return false;
        }
    }

    @Builtin(name = "Py_Ellipsis")
    @GenerateNodeFactory
    public abstract static class PyEllipsisNode extends PythonBuiltinNode {
        @Specialization
        Object run() {
            return PEllipsis.INSTANCE;
        }
    }

    @Builtin(name = "PyModule_SetDocString", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetDocStringNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, PythonModule module, Object doc,
                        @Cached ObjectBuiltins.SetattrNode setattrNode) {
            setattrNode.execute(frame, module, __DOC__, doc);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyModule_NewObject", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String)
    @GenerateNodeFactory
    public abstract static class PyModuleNewObjectNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.PyModuleNewObjectNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object run(VirtualFrame frame, String name,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, PythonBuiltinClassType.PythonModule, new Object[]{name});
        }
    }

    @Builtin(name = "_PyModule_CreateInitialized_PyModule_New", parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String)
    @GenerateNodeFactory
    public abstract static class PyModuleCreateInitializedNewNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.PyModuleCreateInitializedNewNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object run(VirtualFrame frame, String name,
                        @Cached CallNode callNode,
                        @Cached ObjectBuiltins.SetattrNode setattrNode) {
            // see CPython's Objects/moduleobject.c - _PyModule_CreateInitialized for
            // comparison how they handle _Py_PackageContext
            String newModuleName = name;
            PythonContext ctx = getContext();
            String pyPackageContext = ctx.getPyPackageContext();
            if (pyPackageContext != null && pyPackageContext.endsWith(newModuleName)) {
                newModuleName = pyPackageContext;
                ctx.setPyPackageContext(null);
            }
            Object newModule = callNode.execute(frame, PythonBuiltinClassType.PythonModule, new Object[]{newModuleName});
            // TODO: (tfel) I don't think this is the right place to set it, but somehow
            // at least in the import of sklearn.neighbors.dist_metrics through
            // sklearn.neighbors.ball_tree the __package__ attribute seems to be already
            // set in CPython. To not produce a warning, I'm setting it here, although I
            // could not find what CPython really does
            int idx = newModuleName.lastIndexOf(".");
            if (idx > -1) {
                setattrNode.execute(frame, newModule, __PACKAGE__, newModuleName.substring(0, idx));
            }
            return newModule;
        }
    }

    ///////////// dict /////////////

    @Builtin(name = "PyDict_New", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class PyDictNewNode extends PythonVarargsBuiltinNode {

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return execute(frame, self, arguments, keywords);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object run(Object self, Object[] arguments, PKeyword[] keywords) {
            return factory().createDict();
        }
    }

    @Builtin(name = "PyDict_Next", minNumOfPositionalArgs = 2, parameterNames = {"dictObj", "pos"})
    @ArgumentClinic(name = "pos", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class PyDictNextNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.PyDictNextNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "pos < size(frame, dict, sizeNode)", limit = "1")
        Object run(VirtualFrame frame, PDict dict, int pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached PyObjectHashNode hashNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("nativeNull") @Cached GetNativeNullNode getNativeNullNode) {
            try {
                HashingStorageIterator<DictEntry> it = lib.entries(dict.getDictStorage()).iterator();
                DictEntry e = null;
                loopProfile.profileCounted(pos);
                for (int i = 0; loopProfile.inject(i <= pos); i++) {
                    e = it.next();
                }
                return factory().createTuple(new Object[]{e.key, e.value, hashNode.execute(frame, e.key)});
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "isGreaterPosOrNative(frame, pos, dict, sizeNode, getClassNode, isSubtypeNode)", limit = "1")
        Object run(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") int pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Shared("nativeNull") @Cached GetNativeNullNode getNativeNullNode) {
            return getNativeNullNode.execute();
        }

        protected boolean isGreaterPosOrNative(VirtualFrame frame, int pos, Object obj, PyObjectSizeNode sizeNode, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return (isDict(obj) && pos >= size(frame, obj, sizeNode)) || (!isDict(obj) && !isDictSubtype(frame, obj, getClassNode, isSubtypeNode));
        }

        protected boolean isDict(Object obj) {
            return obj instanceof PDict;
        }

        protected int size(VirtualFrame frame, Object dict, PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, dict);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Pop", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictPopNode extends PythonTernaryBuiltinNode {
        @Specialization()
        public Object pop(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached PopNode popNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return popNode.execute(frame, dict, key, defaultValue);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object popNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object pop(VirtualFrame frame, Object obj, Object key, Object defaultValue,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }

    }

    @Builtin(name = "PyDict_Size", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictSizeNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        public Object size(PDict dict,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return lib.length(dict.getDictStorage());
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object sizeNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object size(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Copy", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictCopyNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        public Object copy(PDict dict,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return factory().createDict(lib.copy(dict.getDictStorage()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute(PNone.NONE);
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object copyNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object copy(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        public Object getItem(VirtualFrame frame, PDict dict, Object key,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached BranchProfile noResultProfile,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object res = lib.getItemWithFrame(dict.getDictStorage(), key, hasFrameProfile, frame);
                if (res == null) {
                    noResultProfile.enter();
                    return getNativeNullNode.execute();
                }
                return res;
            } catch (PException e) {
                // PyDict_GetItem suppresses all exceptions for historical reasons
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object getItem(VirtualFrame frame, Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDict(Object obj) {
            return obj instanceof PDict;
        }
    }

    @Builtin(name = "PyDict_GetItemWithError", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictGetItemWithErrorNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        public Object getItem(PDict dict, Object key,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @Cached BranchProfile noResultProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object res = lib.getItem(dict.getDictStorage(), key);
                if (res == null) {
                    noResultProfile.enter();
                    return getNativeNullNode.execute();
                }
                return res;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object getItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictSetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        public Object setItem(VirtualFrame frame, PDict dict, Object key, Object value,
                        @Cached SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, dict, key, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object setItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_SetItem_KnownHash", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class PyDictSetItemKnownHashNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        public Object setItem(VirtualFrame frame, PDict dict, Object key, Object value, Object givenHash,
                        @Cached PyObjectHashNode hashNode,
                        @Cached CastToJavaLongExactNode castToLong,
                        @Cached SetItemNode setItemNode,
                        @Cached BranchProfile wrongHashProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (hashNode.execute(frame, key) != castToLong.execute(givenHash)) {
                    wrongHashProfile.enter();
                    throw raise(PythonBuiltinClassType.AssertionError, HASH_MISMATCH);
                }
                setItemNode.execute(frame, dict, key, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object setItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object givenHash,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_DelItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictDelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object delItem(VirtualFrame frame, PDict dict, Object key,
                        @Cached DelItemNode delItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                delItemNode.execute(frame, dict, key);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object delItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object delItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictContainsNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        public Object contains(VirtualFrame frame, PDict dict, Object key,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("dict.getDictStorage()") HashingStorageLibrary lib,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return lib.hasKeyWithFrame(dict.getDictStorage(), key, hasFrame, frame);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(obj)", "isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object containsNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object contains(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Values", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDictValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, PDict dict,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return listNode.execute(frame, factory().createDictValuesView(dict));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!isDict(obj)", "isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object valuesNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object values(VirtualFrame frame, Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Merge", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictMergeNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = {"override != 0"})
        public Object merge(VirtualFrame frame, PDict a, Object b, @SuppressWarnings("unused") int override,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object updateCallable = lookupAttr.execute(frame, a, "update");
                callNode.execute(updateCallable, new Object[]{b});
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "override == 0", limit = "3")
        public Object merge(VirtualFrame frame, PDict a, PDict b, @SuppressWarnings("unused") int override,
                        @CachedLibrary("a.getDictStorage()") HashingStorageLibrary libA,
                        @CachedLibrary("b.getDictStorage()") HashingStorageLibrary libB,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                HashingStorageIterator<DictEntry> it = libB.entries(b.getDictStorage()).iterator();
                HashingStorage aStorage = a.getDictStorage();
                while (loopProfile.profile(it.hasNext())) {
                    DictEntry e = it.next();
                    if (!libA.hasKey(aStorage, e.key)) {
                        libA.setItemWithFrame(aStorage, e.key, e.value, hasFrameProfile, frame);
                    }
                }
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"override == 0", "!isDict(b)"}, limit = "3")
        public Object merge(VirtualFrame frame, PDict a, Object b, @SuppressWarnings("unused") int override,
                        @Cached LenNode lenNode,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached GetItemNode getKeyNode,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getValueNode,
                        @CachedLibrary("a.getDictStorage()") HashingStorageLibrary libA,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached BranchProfile noKeyProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {

                PList keys = getKeys(frame, a, getAttrNode, callNode, listNode);
                SequenceStorage keysStorage = keys.getSequenceStorage();
                HashingStorage aStorage = a.getDictStorage();
                int size = lenNode.execute(keysStorage);
                loopProfile.profileCounted(size);
                for (int i = 0; loopProfile.inject(i < size); i++) {
                    Object key = getKeyNode.execute(frame, keysStorage, i);
                    if (!libA.hasKey(aStorage, key)) {
                        noKeyProfile.enter();
                        Object value = getValueNode.execute(frame, b, key);
                        aStorage = libA.setItemWithFrame(aStorage, key, value, hasFrameProfile, frame);
                    }
                }
                a.setDictStorage(aStorage);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(a)", "isDictSubtype(frame, a, getClassNode, isSubtypeNode)"})
        public Object mergeNative(VirtualFrame frame, @SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b, @SuppressWarnings("unused") int override,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(a)", "!isDictSubtype(frame, a, getClassNode, isSubtypeNode)"})
        public Object merge(VirtualFrame frame, Object a, @SuppressWarnings("unused") Object b, @SuppressWarnings("unused") int override,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, a), a);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyMapping_Keys", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyMappingKeysNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object keys(VirtualFrame frame, PDict obj,
                        @Cached KeysNode keysNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return listNode.execute(frame, keysNode.execute(frame, obj));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object keys(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return getKeys(frame, obj, getAttrNode, callNode, listNode);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

    }

    private static PList getKeys(VirtualFrame frame, Object obj, PyObjectGetAttr getAttrNode, CallNode callNode, ConstructListNode listNode) {
        Object attr = getAttrNode.execute(frame, obj, KEYS);
        return listNode.execute(frame, callNode.execute(frame, attr));
    }

    @Builtin(name = "PyMapping_Items", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyMappingItemsNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object items(VirtualFrame frame, PDict obj,
                        @Cached ItemsNode itemsNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return listNode.execute(frame, itemsNode.execute(frame, obj));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object items(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object attr = getAttrNode.execute(frame, obj, ITEMS);
                return listNode.execute(frame, callNode.execute(frame, attr));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyMapping_Values", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyMappingValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, PDict obj,
                        @Cached ConstructListNode listNode,
                        @Cached ValuesNode valuesNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return listNode.execute(frame, valuesNode.execute(frame, obj));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object values(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object attr = getAttrNode.execute(frame, obj, VALUES);
                return listNode.execute(frame, callNode.execute(frame, attr));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    /**
     * This is used in the ExternalFunctionNode below, so all arguments passed from Python code into
     * a C function are automatically unwrapped if they are wrapped. This function is also called
     * all over the place in C code to make sure return values have the right representation in
     * Sulong land.
     */
    @Builtin(name = "to_sulong", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToSulongNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object run(Object obj,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(obj);
        }
    }

    @Builtin(name = "PyTruffle_Type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Type extends NativeBuiltin {

        private static final String[] LOOKUP_MODULES = new String[]{
                        PythonCextBuiltins.PYTHON_CEXT,
                        "_weakref",
                        "builtins"
        };

        @Specialization
        @TruffleBoundary
        Object doI(Object typeNameObject) {
            String typeName = CastToJavaStringNode.getUncached().execute(typeNameObject);
            Python3Core core = getCore();
            for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
                if (type.getName().equals(typeName)) {
                    return core.lookupType(type);
                }
            }
            for (String module : LOOKUP_MODULES) {
                Object attribute = core.lookupBuiltinModule(module).getAttribute(typeName);
                if (attribute != PNone.NO_VALUE) {
                    return attribute;
                }
            }
            throw raise(PythonErrorType.KeyError, "'%s'", typeName);
        }
    }

    @Builtin(name = "PyTuple_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTuple_New extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple doGeneric(VirtualFrame frame, Object size,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            return factory().createTuple(new Object[asSizeNode.executeExact(frame, size)]);
        }
    }

    @Builtin(name = "PyTuple_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTuple_SetItem extends PythonTernaryBuiltinNode {
        @Specialization
        static int doManaged(VirtualFrame frame, PythonNativeWrapper selfWrapper, Object position, Object elementWrapper,
                        @Cached AsPythonObjectNode selfAsPythonObjectNode,
                        @Cached AsPythonObjectStealingNode elementAsPythonObjectNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object self = selfAsPythonObjectNode.execute(selfWrapper);
                if (!PGuards.isPTuple(self) || selfWrapper.getRefCount() != 1) {
                    throw raiseNode.raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_P, "PTuple_SetItem");
                }
                PTuple tuple = (PTuple) self;
                Object element = elementAsPythonObjectNode.execute(elementWrapper);
                setItemNode.execute(frame, tuple.getSequenceStorage(), position, element);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = "!isNativeWrapper(tuple)")
        static int doNative(Object tuple, long position, Object element,
                        @Cached PCallCapiFunction callSetItem) {
            // TODO(fa): This path should be avoided since this is called from native code to do a
            // native operation.
            callSetItem.call(NativeCAPISymbol.FUN_PY_TRUFFLE_TUPLE_SET_ITEM, tuple, position, element);
            return 0;
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forTupleAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "CreateBuiltinMethod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CreateBuiltinMethodNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object runWithoutCWrapper(PBuiltinFunction descriptor, Object self) {
            return factory().createBuiltinMethod(self, descriptor);
        }
    }

    @Builtin(name = "import_c_func", minNumOfPositionalArgs = 2, parameterNames = {"name", "capi_library"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class ImportCExtFunction extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.ImportCExtFunctionClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object importCExtFunction(String name, Object capiLibrary,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                Object member = lib.readMember(capiLibrary, name);
                return PExternalFunctionWrapper.createWrapperFunction(name, member, null, 0,
                                PExternalFunctionWrapper.DIRECT, getLanguage(), factory(), true);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateUncached
    abstract static class CreateFunctionNode extends PNodeWithContext {

        abstract Object execute(String name, Object callable, Object wrapper, Object type, Object flags, PythonObjectFactory factory);

        @Specialization(guards = {"isTypeNode.execute(type)", "isNoValue(wrapper)"}, limit = "3")
        static Object doPythonCallableWithoutWrapper(@SuppressWarnings("unused") String name, PythonNativeWrapper callable,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object type,
                        @SuppressWarnings("unused") Object flags,
                        @SuppressWarnings("unused") PythonObjectFactory factory,
                        @CachedLibrary("callable") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            return nativeWrapperLibrary.getDelegate(callable);
        }

        @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
        @TruffleBoundary
        Object doPythonCallable(String name, PythonNativeWrapper callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            Object managedCallable = callable.getDelegateSlowPath();
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags, signature, getLanguage(), factory, false);
            return function != null ? function : managedCallable;
        }

        @Specialization(guards = {"isTypeNode.execute(type)", "isDecoratedManagedFunction(callable)", "isNoValue(wrapper)"}, limit = "1")
        @SuppressWarnings("unused")
        static Object doDecoratedManagedWithoutWrapper(@SuppressWarnings("unused") String name, PyCFunctionDecorator callable, PNone wrapper, Object type, Object flags, PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            return nativeWrapperLibrary.getDelegate(callable.getNativeFunction());
        }

        @Specialization(guards = "isDecoratedManagedFunction(callable)")
        @TruffleBoundary
        static Object doDecoratedManaged(String name, PyCFunctionDecorator callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary nativeWrapperLibrary) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            Object managedCallable = nativeWrapperLibrary.getDelegate(callable.getNativeFunction());
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags,
                            signature, PythonLanguage.get(nativeWrapperLibrary), factory, false);
            if (function != null) {
                return function;
            }

            // Special case: if the returned 'wrappedCallTarget' is null, this indicates we want to
            // call a Python callable without wrapping and arguments conversion. So, directly use
            // the callable.
            return managedCallable;
        }

        @Specialization(guards = {"isTypeNode.execute(type)", "!isNativeWrapper(callable)"}, limit = "1")
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithType(String name, Object callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, flags,
                            signature, getLanguage(), factory, true);
        }

        @Specialization(guards = {"isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutType(String name, Object callable, int signature, @SuppressWarnings("unused") PNone type, int flags, PythonObjectFactory factory) {
            return doNativeCallableWithType(name, callable, signature, null, flags, factory, null);
        }

        @Specialization(guards = {"isTypeNode.execute(type)", "isNoValue(wrapper)", "!isNativeWrapper(callable)"}, limit = "1")
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapper(String name, Object callable, Object type,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object flags, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, 0,
                            PExternalFunctionWrapper.DIRECT, getLanguage(), factory, true);
        }

        @Specialization(guards = {"isNoValue(wrapper)", "isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapperAndType(String name, Object callable, PNone wrapper, @SuppressWarnings("unused") PNone type, Object flags, PythonObjectFactory factory) {
            return doNativeCallableWithoutWrapper(name, callable, null, wrapper, flags, factory, null);
        }

        static boolean isNativeWrapper(Object obj) {
            return CApiGuards.isNativeWrapper(obj) || isDecoratedManagedFunction(obj);
        }

        static boolean isDecoratedManagedFunction(Object obj) {
            return obj instanceof PyCFunctionDecorator && CApiGuards.isNativeWrapper(((PyCFunctionDecorator) obj).getNativeFunction());
        }
    }

    @Builtin(name = "PyErr_Restore", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrRestoreNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object run(PNone typ, PNone val, PNone tb) {
            getContext().setCurrentException(getLanguage(), null);
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            context.setCurrentException(language, PException.fromExceptionInfo(val, (LazyTraceback) null, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            context.setCurrentException(language, PException.fromExceptionInfo(val, tb, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyErr_Fetch", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyErrFetchNode extends NativeBuiltin {
        @Specialization
        public Object run(Object module,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetTracebackNode getTracebackNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            PException currentException = getThreadStateNode.getCurrentException();
            Object result;
            if (currentException == null) {
                result = getNativeNullNode.execute(module);
            } else {
                PBaseException exception = currentException.getEscapedException();
                Object traceback = null;
                if (currentException.getTraceback() != null) {
                    traceback = getTracebackNode.execute(currentException.getTraceback());
                }
                if (traceback == null) {
                    traceback = getNativeNullNode.execute(module);
                }
                result = factory().createTuple(new Object[]{getClassNode.execute(exception), exception, traceback});
                getThreadStateNode.setCurrentException(null);
            }
            return result;
        }
    }

    @Builtin(name = "PyErr_Occurred", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyErrOccurred extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object errorMarker,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached GetClassNode getClassNode) {
            PException currentException = getThreadStateNode.getCurrentException();
            if (currentException != null) {
                // getClassNode acts as a branch profile
                return getClassNode.execute(currentException.getUnreifiedException());
            }
            return errorMarker;
        }
    }

    @Builtin(name = "PyErr_SetExcInfo", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrSetExcInfo extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doClear(PNone typ, PNone val, PNone tb) {
            getContext().setCaughtException(getLanguage(), PException.NO_EXCEPTION);
            return PNone.NONE;
        }

        @Specialization
        Object doFull(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            context.setCaughtException(language, PException.fromExceptionInfo(val, tb, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }

        @Specialization
        Object doWithoutTraceback(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb) {
            return doFull(typ, val, null);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFallback(Object typ, Object val, Object tb) {
            // TODO we should still store the values to return them with 'PyErr_GetExcInfo' (or
            // 'sys.exc_info')
            return PNone.NONE;
        }
    }

    /**
     * Exceptions are usually printed using the traceback module or the hook function
     * {@code sys.excepthook}. This is the last resort if the hook function itself failed.
     */
    @Builtin(name = "PyErr_Display", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrDisplay extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object run(Object typ, PBaseException val, Object tb) {
            if (val.getException() != null) {
                ExceptionUtils.printPythonLikeStackTrace(val.getException());
            }
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "PyTruffle_WriteUnraisable", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleWriteUnraisable extends PythonBuiltinNode {

        @Specialization
        static Object run(PBaseException exception, Object object,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached WriteUnraisableNode writeUnraisableNode) {
            writeUnraisableNode.execute(null, exception, null, (object instanceof PNone) ? PNone.NONE : object);
            getThreadStateNode.setCaughtException(PException.NO_EXCEPTION);
            return PNone.NO_VALUE;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyUnicode_New", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyUnicodeNewNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(Object ptr, int elementSize, int isAscii,
                        @Cached CExtNodes.ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(factory().createString(new NativeCharSequence(ptr, elementSize, isAscii != 0)));
        }
    }

    @Builtin(name = "PyUnicode_FromString", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyUnicodeFromStringNode extends PythonBuiltinNode {
        @Specialization
        PString run(String str) {
            return factory().createString(str);
        }

        @Specialization
        PString run(PString str) {
            return str;
        }
    }

    @Builtin(name = "PyUnicode_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyUnicodeContains extends PythonBinaryBuiltinNode {
        @Specialization
        int contains(VirtualFrame frame, Object haystack, Object needle,
                        @Cached StringBuiltins.ContainsNode containsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return containsNode.executeBool(haystack, needle) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "do_richcompare", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "op == 0")
        Object op0(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.LtNode compNode) {
            return compNode.executeObject(frame, a, b);
        }

        @Specialization(guards = "op == 1")
        Object op1(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.LeNode compNode) {
            return compNode.executeObject(frame, a, b);
        }

        @Specialization(guards = "op == 2")
        Object op2(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.EqNode compNode) {
            return compNode.executeObject(frame, a, b);
        }

        @Specialization(guards = "op == 3")
        Object op3(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.NeNode compNode) {
            return compNode.executeObject(frame, a, b);
        }

        @Specialization(guards = "op == 4")
        Object op4(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.GtNode compNode) {
            return compNode.executeObject(frame, a, b);
        }

        @Specialization(guards = "op == 5")
        Object op5(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.GeNode compNode) {
            return compNode.executeObject(frame, a, b);
        }
    }

    @Builtin(name = "PyTruffle_SetAttr", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObject_Setattr extends PythonTernaryBuiltinNode {

        abstract Object execute(Object object, String key, Object value);

        @Specialization
        Object doBuiltinClass(PythonBuiltinClass object, String key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization
        Object doNativeClass(PythonNativeClass object, String key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPythonBuiltinClass(object)"})
        Object doObject(PythonObject object, String key, Object value,
                        @Exclusive @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode) {
            writeAttrToDynamicObjectNode.execute(object.getStorage(), key, value);
            return PNone.NONE;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Set_Native_Slots", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleSetNativeSlots extends NativeBuiltin {
        static final HiddenKey NATIVE_SLOTS = new HiddenKey("__native_slots__");

        @Specialization
        static int doPythonClass(PythonClassNativeWrapper pythonClassWrapper, Object nativeGetSets, Object nativeMembers,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            Object pythonClass = asPythonObjectNode.execute(pythonClassWrapper);
            assert pythonClass instanceof PythonManagedClass;
            writeAttrNode.execute(pythonClass, NATIVE_SLOTS, new Object[]{nativeGetSets, nativeMembers});
            return 0;
        }
    }

    @Builtin(name = "PyTruffle_Get_Inherited_Native_Slots", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyTruffleGetInheritedNativeSlots extends NativeBuiltin {
        private static final int INDEX_GETSETS = 0;
        private static final int INDEX_MEMBERS = 1;

        /**
         * A native class may inherit from a managed class. However, the managed class may define
         * custom slots at a time where the C API is not yet loaded. So we need to check if any of
         * the base classes defines custom slots and adapt the basicsize to allocate space for the
         * slots and add the native member slot descriptors.
         */
        @Specialization
        Object slots(Object module, Object pythonClass, String subKey,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            int idx;
            if ("getsets".equals(subKey)) {
                idx = INDEX_GETSETS;
            } else if ("members".equals(subKey)) {
                idx = INDEX_MEMBERS;
            } else {
                return getNativeNullNode.execute(module);
            }

            Object[] values = collect(getMroStorageNode.execute(pythonClass), idx);
            return new PySequenceArrayWrapper(factory().createTuple(values), Long.BYTES);
        }

        @TruffleBoundary
        private static Object[] collect(MroSequenceStorage mro, int idx) {
            ArrayList<Object> l = new ArrayList<>();
            int mroLength = mro.length();
            for (int i = 0; i < mroLength; i++) {
                PythonAbstractClass kls = mro.getItemNormalized(i);
                Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(kls, PyTruffleSetNativeSlots.NATIVE_SLOTS);
                if (value != PNone.NO_VALUE) {
                    Object[] tuple = (Object[]) value;
                    assert tuple.length == 2;
                    l.add(new PythonAbstractNativeObject(tuple[idx]));
                }
            }
            return l.toArray();
        }
    }

    @Builtin(name = "Py_NoValue")
    @GenerateNodeFactory
    abstract static class Py_NoValue extends PythonBuiltinNode {
        @Specialization
        PNone doNoValue() {
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "Py_None")
    @GenerateNodeFactory
    abstract static class PyNoneNode extends PythonBuiltinNode {
        @Specialization
        PNone doNativeNone() {
            return PNone.NONE;
        }
    }

    @TypeSystemReference(PythonTypes.class)
    abstract static class NativeBuiltin extends PythonBuiltinNode {

        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @Child private PRaiseNativeNode raiseNativeNode;

        protected void transformToNative(VirtualFrame frame, PException p) {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            transformExceptionToNativeNode.execute(frame, p);
        }

        protected Object raiseNative(VirtualFrame frame, Object defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
            return ensureRaiseNativeNode().execute(frame, defaultValue, errType, fmt, args);
        }

        protected Object raiseBadArgument(VirtualFrame frame, Object errorMarker) {
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data, int offset, int length) {
            return ByteBuffer.wrap(data, offset, length);
        }

        private PRaiseNativeNode ensureRaiseNativeNode() {
            if (raiseNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNativeNode = insert(PRaiseNativeNodeGen.create());
            }
            return raiseNativeNode;
        }
    }

    abstract static class NativeUnicodeBuiltin extends NativeBuiltin {
        @TruffleBoundary
        protected static CharBuffer allocateCharBuffer(int cap) {
            return CharBuffer.allocate(cap);
        }

        @TruffleBoundary
        protected static String toString(CharBuffer cb) {
            int len = cb.position();
            if (len > 0) {
                cb.rewind();
                return cb.subSequence(0, len).toString();
            }
            return "";
        }

        @TruffleBoundary
        protected static int remaining(ByteBuffer cb) {
            return cb.remaining();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyLong_AsPrimitive", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyLongAsPrimitive extends PythonTernaryBuiltinNode {
        @Child private ResolveHandleNode resolveHandleNode;
        @Child private ToJavaNode toJavaNode;
        @CompilationFinal private ValueProfile pointerClassProfile;
        @Child private ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @Child private CastToNativeLongNode castToNativeLongNode;
        @Child private GetClassNode getClassNode;
        @Child private IsSubtypeNode isSubtypeNode;

        public abstract Object executeWith(VirtualFrame frame, Object object, int mode, long targetTypeSize);

        public abstract long executeLong(VirtualFrame frame, Object object, int mode, long targetTypeSize);

        public abstract int executeInt(VirtualFrame frame, Object object, int mode, long targetTypeSize);

        @Specialization(rewriteOn = {UnexpectedWrapperException.class, UnexpectedResultException.class})
        long doPrimitiveNativeWrapperToLong(VirtualFrame frame, Object object, int mode, long targetTypeSize) throws UnexpectedWrapperException, UnexpectedResultException {
            Object resolvedPointer = ensureResolveHandleNode().execute(object);
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    PrimitiveNativeWrapper wrapper = (PrimitiveNativeWrapper) resolvedPointer;
                    if (requiredPInt(mode) && !wrapper.isSubtypeOfInt()) {
                        throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                    }
                    return ensureConvertPIntToPrimitiveNode().executeLong(wrapper, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnexpectedWrapperException.INSTANCE;
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException e) {
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return -1;
            }
        }

        @Specialization(replaces = "doPrimitiveNativeWrapperToLong")
        Object doGeneric(VirtualFrame frame, Object objectPtr, int mode, long targetTypeSize) {
            Object resolvedPointer = ensurePointerClassProfile().profile(ensureResolveHandleNode().execute(objectPtr));
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    PrimitiveNativeWrapper wrapper = (PrimitiveNativeWrapper) resolvedPointer;
                    if (requiredPInt(mode) && !wrapper.isSubtypeOfInt()) {
                        throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                    }
                    return ensureConvertPIntToPrimitiveNode().execute(wrapper, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                }
                /*
                 * The 'mode' parameter is usually a constant since this function is primarily used
                 * in 'PyLong_As*' API functions that pass a fixed mode. So, there is not need to
                 * profile the value and even if it is not constant, it is profiled implicitly.
                 */
                Object object = ensureToJavaNode().execute(resolvedPointer);
                if (requiredPInt(mode) && !ensureIsSubtypeNode().execute(getPythonClass(object), PythonBuiltinClassType.PInt)) {
                    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                // the 'ConvertPIntToPrimitiveNode' uses 'AsNativePrimitive' which does coercion
                Object coerced = ensureConvertPIntToPrimitiveNode().execute(object, signed(mode), PInt.intValueExact(targetTypeSize), exact(mode));
                return ensureCastToNativeLongNode().execute(coerced);
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException e) {
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return -1;
            }
        }

        private static int signed(int mode) {
            return mode & 0x1;
        }

        private static boolean requiredPInt(int mode) {
            return (mode & 0x2) != 0;
        }

        private static boolean exact(int mode) {
            return (mode & 0x4) == 0;
        }

        private ResolveHandleNode ensureResolveHandleNode() {
            if (resolveHandleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveHandleNode = insert(ResolveHandleNodeGen.create());
            }
            return resolveHandleNode;
        }

        private ToJavaNode ensureToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNodeGen.create());
            }
            return toJavaNode;
        }

        private ValueProfile ensurePointerClassProfile() {
            if (pointerClassProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pointerClassProfile = ValueProfile.createClassProfile();
            }
            return pointerClassProfile;
        }

        private ConvertPIntToPrimitiveNode ensureConvertPIntToPrimitiveNode() {
            if (convertPIntToPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                convertPIntToPrimitiveNode = insert(ConvertPIntToPrimitiveNodeGen.create());
            }
            return convertPIntToPrimitiveNode;
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }

        private CastToNativeLongNode ensureCastToNativeLongNode() {
            if (castToNativeLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToNativeLongNode = insert(CastToNativeLongNodeGen.create());
            }
            return castToNativeLongNode;
        }

        private Object getPythonClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(object);
        }

        private IsSubtypeNode ensureIsSubtypeNode() {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode;
        }

        static final class UnexpectedWrapperException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
            static final UnexpectedWrapperException INSTANCE = new UnexpectedWrapperException();
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromWchar", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_FromWchar extends NativeUnicodeBuiltin {
        @Child private UnicodeFromWcharNode unicodeFromWcharNode;
        @Child private CExtNodes.ToNewRefNode toSulongNode;

        @Specialization
        Object doInt(VirtualFrame frame, Object arr, int elementSize, Object errorMarker) {
            try {
                /*
                 * If we receive a native wrapper here, we assume that it is one of the wrappers
                 * that emulates some C array (e.g. CArrayWrapper or PySequenceArrayWrapper). Those
                 * wrappers are directly handled by the node. Otherwise, it is assumed that the
                 * object is a typed pointer object.
                 */
                return ensureToSulongNode().execute(ensureUnicodeFromWcharNode().execute(arr, elementSize));
            } catch (PException e) {
                transformToNative(frame, e);
                return errorMarker;
            }
        }

        @Specialization(limit = "1")
        Object doGeneric(VirtualFrame frame, Object arr, Object elementSize, Object errorMarker,
                        @CachedLibrary("elementSize") InteropLibrary elementSizeLib) {

            if (elementSizeLib.fitsInInt(elementSize)) {
                try {
                    return doInt(frame, arr, elementSizeLib.asInt(elementSize), errorMarker);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        private UnicodeFromWcharNode ensureUnicodeFromWcharNode() {
            if (unicodeFromWcharNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                unicodeFromWcharNode = insert(UnicodeFromWcharNodeGen.create());
            }
            return unicodeFromWcharNode;
        }

        private CExtNodes.ToNewRefNode ensureToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToNewRefNodeGen.create());
            }
            return toSulongNode;
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromUTF8", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_FromUTF8 extends NativeBuiltin {

        @Specialization
        Object doBytes(VirtualFrame frame, Object o, Object errorMarker,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return decodeUTF8(getByteArrayNode.execute(o, -1));
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeError, "%m", e);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "%m", e);
            } catch (OverflowException e) {
                return raiseNative(frame, errorMarker, OverflowError, ErrorMessages.INPUT_TOO_LONG);
            }
        }

        @TruffleBoundary
        private static String decodeUTF8(byte[] data) throws CharacterCodingException {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            return decoder.decode(wrap(data)).toString();
        }
    }

    abstract static class NativeEncoderNode extends NativeBuiltin {
        private final Charset charset;

        protected NativeEncoderNode(Charset charset) {
            this.charset = charset;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(VirtualFrame frame, PString s, @SuppressWarnings("unused") PNone errors, Object error_marker,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            return doUnicode(frame, s, "strict", error_marker, encodeNativeStringNode);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, PString s, String errors, Object error_marker,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            try {
                return factory().createBytes(encodeNativeStringNode.execute(charset, s, errors));
            } catch (PException e) {
                transformToNative(frame, e);
                return error_marker;
            }
        }

        @Fallback
        Object doUnicode(VirtualFrame frame, @SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors, Object errorMarker) {
            return raiseBadArgument(frame, errorMarker);
        }
    }

    @Builtin(name = "_PyUnicode_AsUTF8String", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyUnicode_AsUTF8String extends NativeEncoderNode {

        protected _PyUnicode_AsUTF8String() {
            super(StandardCharsets.UTF_8);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsLatin1String", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Unicode_AsLatin1String extends NativeEncoderNode {
        protected _PyTruffle_Unicode_AsLatin1String() {
            super(StandardCharsets.ISO_8859_1);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsASCIIString", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Unicode_AsASCIIString extends NativeEncoderNode {
        protected _PyTruffle_Unicode_AsASCIIString() {
            super(StandardCharsets.US_ASCII);
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsUnicodeAndSize", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_AsUnicodeAndSize extends NativeBuiltin {
        @Specialization
        @TruffleBoundary
        Object doUnicode(PString s) {
            char[] charArray = s.getValue().toCharArray();
            // stuff into byte[]
            ByteBuffer allocate = ByteBuffer.allocate(charArray.length * 2);
            for (int i = 0; i < charArray.length; i++) {
                allocate.putChar(charArray[i]);
            }
            return getContext().getEnv().asGuestValue(allocate.array());
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Unicode_DecodeUTF32", minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_DecodeUTF32 extends NativeUnicodeBuiltin {

        @Specialization
        Object doUnicodeStringErrors(VirtualFrame frame, Object o, long size, String errors, int byteorder, Object errorMarker,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getByteArrayNode") @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return toSulongNode.execute(decodeUTF32(getByteArrayNode.execute(o, size), (int) size, errors, byteorder));
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeEncodeError, "%m", e);
            } catch (IllegalArgumentException e) {
                String csName = Charsets.getUTF32Name(byteorder);
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ENCODING, csName);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "%m", e);
            } catch (OverflowException e) {
                return raiseNative(frame, errorMarker, OverflowError, ErrorMessages.INPUT_TOO_LONG);
            }
        }

        @Specialization(replaces = "doUnicodeStringErrors")
        Object doUnicode(VirtualFrame frame, Object o, long size, Object errors, int byteorder, Object errorMarker,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getByteArrayNode") @Cached GetByteArrayNode getByteArrayNode) {
            Object perrors = asPythonObjectNode.execute(errors);
            assert perrors == PNone.NO_VALUE || perrors instanceof String;
            return doUnicodeStringErrors(frame, o, size, perrors == PNone.NO_VALUE ? "strict" : (String) perrors, byteorder, errorMarker, toSulongNode, getByteArrayNode);
        }

        @TruffleBoundary
        private String decodeUTF32(byte[] data, int size, String errors, int byteorder) throws CharacterCodingException {
            CharsetDecoder decoder = Charsets.getUTF32Charset(byteorder).newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            CharBuffer decode = decoder.onMalformedInput(action).onUnmappableCharacter(action).decode(wrap(data, 0, size));
            return decode.toString();
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsWideChar", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_AsWideChar extends NativeUnicodeBuiltin {
        @Child private UnicodeAsWideCharNode asWideCharNode;

        @Specialization
        Object doUnicode(VirtualFrame frame, Object s, long elementSize, @SuppressWarnings("unused") PNone elements, Object errorMarker,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr) {
            return doUnicode(frame, s, elementSize, -1, errorMarker, castStr);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, Object s, long elementSize, long elements, Object errorMarker,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr) {
            try {
                if (asWideCharNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asWideCharNode = insert(UnicodeAsWideCharNodeGen.create());
                }

                PBytes wchars = asWideCharNode.executeLittleEndian(castStr.execute(s), elementSize, elements);
                if (wchars != null) {
                    return wchars;
                } else {
                    return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, "%m", e);
            }
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, PInt elementSize, @SuppressWarnings("unused") PNone elements, Object errorMarker,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr) {
            try {
                return doUnicode(frame, s, elementSize.longValueExact(), -1, errorMarker, castStr);
            } catch (OverflowException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.INVALID_PARAMS);
            }
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, PInt elementSize, PInt elements, Object errorMarker,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr) {
            try {
                return doUnicode(frame, s, elementSize.longValueExact(), elements.longValueExact(), errorMarker, castStr);
            } catch (OverflowException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.INVALID_PARAMS);
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_AsString", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_AsString extends NativeBuiltin {
        @Specialization
        Object doBytes(PBytes bytes, @SuppressWarnings("unused") Object errorMarker) {
            return new PySequenceArrayWrapper(bytes, 1);
        }

        @Specialization
        Object doUnicode(PString str, @SuppressWarnings("unused") Object errorMarker) {
            return new CStringWrapper(str.getValue());
        }

        @Fallback
        Object doUnicode(VirtualFrame frame, Object o, Object errorMarker) {
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", o);
        }
    }

    @Builtin(name = "PyHash_Imag")
    @GenerateNodeFactory
    abstract static class PyHashImagNode extends PythonBuiltinNode {
        @Specialization
        long getHash() {
            return SysModuleBuiltins.HASH_IMAG;
        }
    }

    @Builtin(name = "PyTruffleHash_InitSecret", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHashGetSecret extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(Object secretPtr) {
            try {
                InteropLibrary lib = InteropLibrary.getUncached(secretPtr);
                byte[] secret = getContext().getHashSecret();
                int len = (int) lib.getArraySize(secretPtr);
                for (int i = 0; i < len; i++) {
                    lib.writeArrayElement(secretPtr, i, secret[i]);
                }
                return 0;
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = "PyTruffleFrame_New", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffleFrameNewNode extends PythonBuiltinNode {
        @Specialization
        Object newFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
            Object frameLocals;
            if (locals == null || PGuards.isPNone(locals)) {
                frameLocals = factory().createDict();
            } else {
                frameLocals = locals;
            }
            return factory().createPFrame(threadState, code, globals, frameLocals);
        }
    }

    @Builtin(name = "PyTraceBack_Here", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTraceBackHereNode extends PythonUnaryBuiltinNode {
        @Specialization
        int tbHere(PFrame frame,
                        @Cached GetTracebackNode getTracebackNode) {
            PythonLanguage language = getLanguage();
            PythonThreadState threadState = getContext().getThreadState(language);
            PException currentException = threadState.getCurrentException();
            if (currentException != null) {
                PTraceback traceback = null;
                if (currentException.getTraceback() != null) {
                    traceback = getTracebackNode.execute(currentException.getTraceback());
                }
                PTraceback newTraceback = factory().createTraceback(frame, frame.getLine(), traceback);
                boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(language);
                threadState.setCurrentException(PException.fromExceptionInfo(currentException.getUnreifiedException(), newTraceback, withJavaStacktrace));
            }

            return 0;
        }
    }

    @Builtin(name = "_PyTraceback_Add", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTracebackAdd extends PythonTernaryBuiltinNode {
        @Specialization
        Object tbHere(String funcname, String filename, int lineno,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached PyTraceBackHereNode pyTraceBackHereNode) {
            PCode code = createCodeNode.execute(null, 0, 0, 0, 0, 0, 0,
                            EMPTY_BYTE_ARRAY, EMPTY_OBJECT_ARRAY, EMPTY_OBJECT_ARRAY, EMPTY_OBJECT_ARRAY, EMPTY_OBJECT_ARRAY, EMPTY_OBJECT_ARRAY,
                            filename, funcname, lineno, EMPTY_BYTE_ARRAY);
            PFrame frame = factory().createPFrame(null, code, factory().createDict(), factory().createDict());
            pyTraceBackHereNode.execute(null, frame);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_Set_SulongType", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Set_SulongType extends NativeBuiltin {

        @Specialization(limit = "1")
        static Object doPythonObject(PythonClassNativeWrapper klass, Object ptr,
                        @CachedLibrary("klass") PythonNativeWrapperLibrary lib) {
            ((PythonManagedClass) lib.getDelegate(klass)).setSulongType(ptr);
            return ptr;
        }
    }

    @Builtin(name = "PyTruffle_SetBufferProcs", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleSetBufferProcs extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doNativeWrapper(PythonClassNativeWrapper nativeWrapper, Object getBufferProc, Object releaseBufferProc) {
            nativeWrapper.setGetBufferProc(getBufferProc);
            nativeWrapper.setReleaseBufferProc(releaseBufferProc);
            return PNone.NO_VALUE;
        }

        @Specialization
        static Object doPythonObject(PythonManagedClass obj, Object getBufferProc, Object releaseBufferProc) {
            return doNativeWrapper(obj.getClassNativeWrapper(), getBufferProc, releaseBufferProc);
        }
    }

    @Builtin(name = "PyMemoryView_FromObject", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleMemoryViewFromObject extends NativeBuiltin {
        @Specialization
        Object wrap(VirtualFrame frame, Object object,
                        @Cached PyMemoryViewFromObject memoryViewNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return memoryViewNode.execute(frame, object);
            } catch (PException e) {
                transformToNative(frame, e);
                return getNativeNullNode.execute();
            }
        }
    }

    // Called without landing node
    @Builtin(name = "PyTruffle_MemoryViewFromBuffer", minNumOfPositionalArgs = 11)
    @GenerateNodeFactory
    abstract static class PyTrufflMemoryViewFromBuffer extends NativeBuiltin {

        @Specialization
        Object wrap(VirtualFrame frame, Object bufferStructPointer, Object ownerObj, Object lenObj,
                        Object readonlyObj, Object itemsizeObj, Object formatObj,
                        Object ndimObj, Object bufPointer, Object shapePointer, Object stridesPointer, Object suboffsetsPointer,
                        @Cached ConditionProfile zeroDimProfile,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                int ndim = castToIntNode.execute(ndimObj);
                int itemsize = castToIntNode.execute(itemsizeObj);
                int len = castToIntNode.execute(lenObj);
                boolean readonly = castToIntNode.execute(readonlyObj) != 0;
                String format = (String) asPythonObjectNode.execute(formatObj);
                Object owner = lib.isNull(ownerObj) ? null : asPythonObjectNode.execute(ownerObj);
                int[] shape = null;
                int[] strides = null;
                int[] suboffsets = null;
                if (zeroDimProfile.profile(ndim > 0)) {
                    if (!lib.isNull(shapePointer)) {
                        shape = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            shape[i] = castToIntNode.execute(lib.readArrayElement(shapePointer, i));
                        }
                    } else {
                        assert ndim == 1;
                        shape = new int[1];
                        shape[0] = len / itemsize;
                    }
                    if (!lib.isNull(stridesPointer)) {
                        strides = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            strides[i] = castToIntNode.execute(lib.readArrayElement(stridesPointer, i));
                        }
                    } else {
                        strides = PMemoryView.initStridesFromShape(ndim, itemsize, shape);
                    }
                    if (!lib.isNull(suboffsetsPointer)) {
                        suboffsets = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            suboffsets[i] = castToIntNode.execute(lib.readArrayElement(suboffsetsPointer, i));
                        }
                    }
                }
                Object buffer = new NativeSequenceStorage(bufPointer, len, len, SequenceStorage.ListStorageType.Byte);
                int flags = initFlagsNode.execute(ndim, itemsize, shape, strides, suboffsets);
                BufferLifecycleManager bufferLifecycleManager = null;
                if (!lib.isNull(bufferStructPointer)) {
                    bufferLifecycleManager = new NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromType(bufferStructPointer);
                }
                PMemoryView memoryview = factory().createMemoryView(getContext(), bufferLifecycleManager, buffer, owner, len, readonly, itemsize,
                                BufferFormat.forMemoryView(format),
                                format, ndim, bufPointer, 0, shape, strides, suboffsets, flags);
                return toNewRefNode.execute(memoryview);
            } catch (PException e) {
                transformToNative(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute());
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = "PyThreadState_Get")
    @GenerateNodeFactory
    abstract static class PyThreadStateGet extends NativeBuiltin {

        @Specialization
        PThreadState get() {
            return PThreadState.getThreadState(getLanguage(), getContext());
        }
    }

    @Builtin(name = "PyTruffle_SeqIter_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SeqIterNewNode extends PythonBuiltinNode {
        @Specialization
        PSequenceIterator call(Object seq) {
            return factory().createSequenceIterator(seq);
        }
    }

    @Builtin(name = "PyTruffle_BuiltinMethod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodNode extends PythonBuiltinNode {
        @Specialization
        Object call(Object self, PBuiltinFunction function) {
            return factory().createBuiltinMethod(self, function);
        }
    }

    @Builtin(name = "PyTruffle_Bytes_EmptyWithCapacity", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_EmptyWithCapacity extends PythonUnaryBuiltinNode {

        @Specialization
        PBytes doInt(int size) {
            return factory().createBytes(new byte[size]);
        }

        @Specialization(rewriteOn = OverflowException.class)
        PBytes doLong(long size) throws OverflowException {
            return doInt(PInt.intValueExact(size));
        }

        @Specialization(replaces = "doLong")
        PBytes doLongOvf(long size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(PInt.intValueExact(size));
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(IndexError, size);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        PBytes doPInt(PInt size) throws OverflowException {
            return doInt(size.intValueExact());
        }

        @Specialization(replaces = "doPInt")
        PBytes doPIntOvf(PInt size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(size.intValueExact());
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(IndexError, size);
            }
        }
    }

    private abstract static class UpcallLandingNode extends PythonVarargsBuiltinNode {
        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, self, arguments, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = "PyTruffle_Upcall_Borrowed", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallBorrowedNode extends UpcallLandingNode {

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toSulongNode.execute(getNativeNullNode.execute(cextModule));
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_NewRef", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallNewRefNode extends UpcallLandingNode {

        @Specialization
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toNewRefNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(cextModule));
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_l", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallLNode extends UpcallLandingNode {

        @Specialization
        static Object upcall(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CastToNativeLongNode asLongNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = asLongNode.execute(upcallNode.execute(frame, args));
                assert result instanceof Long || result instanceof PythonNativeVoidPtr;
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_d", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallDNode extends UpcallLandingNode {

        @Specialization
        double upcall(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CastToJavaDoubleNode castToDoubleNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return castToDoubleNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1.0;
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_ptr", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallPtrNode extends UpcallLandingNode {

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return upcallNode.execute(frame, args);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getNativeNullNode.execute(cextModule);
            }
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_Borrowed", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextBorrowedNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, args));
        }

        public static boolean isStringCallee(Object[] args) {
            return PGuards.isString(args[0]);
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_NewRef", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextNewRefNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(upcallNode.execute(frame, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_d", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextDNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        double upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("castToDoubleNode") @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        double doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("castToDoubleNode") @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(upcallNode.execute(frame, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_l", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextLNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("asLong") @Cached CastToNativeLongNode asLongNode) {
            return asLongNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("asLong") @Cached CastToNativeLongNode asLongNode) {
            return asLongNode.execute(upcallNode.execute(frame, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_ptr", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextPtrNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode) {
            return upcallNode.execute(frame, cextModule, args);
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode) {
            return upcallNode.execute(frame, args);
        }
    }

    /**
     * Inserts a {@link MayRaiseNode} that wraps the body of the function. This will return a new
     * function object with a rewritten AST. However, we use a cache for the call targets and thus
     * the rewritten-ASTs will also be shared if appropriate.
     */
    @Builtin(name = "make_may_raise_wrapper", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MakeMayRaiseWrapperNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object make(PFunction func, Object errorResultObj) {
            RootCallTarget originalCallTarget = GetCallTargetNode.getUncached().execute(func);

            // Replace the first expression node with the MayRaiseNode
            RootCallTarget wrapperCallTarget = getLanguage().createCachedCallTarget(
                            l -> ((FunctionRootNode) originalCallTarget.getRootNode()).rewriteWithNewSignature(GetSignatureNode.getUncached().execute(func), node -> false,
                                            body -> MayRaiseNode.create(body, convertToEnum(errorResultObj))),
                            MakeMayRaiseWrapperNode.class, originalCallTarget);

            // Although we could theoretically re-use the old function instance, we create a new one
            // to be on the safe side.
            return factory().createFunction(func.getName(), func.getQualname(), func.getEnclosingClassName(), factory().createCode(wrapperCallTarget), func.getGlobals(), func.getDefaults(),
                            func.getKwDefaults(), func.getClosure(), func.getCodeStableAssumption(), func.getCodeStableAssumption());
        }

        private MayRaiseErrorResult convertToEnum(Object object) {
            if (PGuards.isNone(object)) {
                return MayRaiseErrorResult.NONE;
            } else if (object instanceof Integer) {
                int i = (int) object;
                if (i == -1) {
                    return MayRaiseErrorResult.INT;
                }
            } else if (object instanceof Double) {
                double i = (double) object;
                if (i == -1.0) {
                    return MayRaiseErrorResult.FLOAT;
                }
            } else if (object instanceof PythonNativeNull || PGuards.isNoValue(object)) {
                return MayRaiseErrorResult.NATIVE_NULL;
            }
            throw raise(PythonErrorType.TypeError, "invalid error result value");
        }
    }

    @Builtin(name = "to_long", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsLong extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, Object object,
                        @Cached CastToNativeLongNode asLongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = asLongNode.execute(object);
                assert result instanceof Long || result instanceof PythonNativeVoidPtr;
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "to_double", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsDouble extends PythonUnaryBuiltinNode {
        @Specialization
        double doIt(Object object,
                        @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(object);
        }
    }

    @Builtin(name = "PyTruffle_Register_NULL", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Register_NULL extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
            Object wrapper = readAttrNode.execute(getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT), NATIVE_NULL);
            if (wrapper instanceof PythonNativeNull) {
                ((PythonNativeNull) wrapper).setPtr(object);
            }

            return wrapper;
        }
    }

    @Builtin(name = "PyTruffle_HandleCache_Create", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHandleCacheCreate extends PythonUnaryBuiltinNode {
        @Specialization
        static Object createCache(Object ptrToResolveHandle) {
            return new HandleCache(ptrToResolveHandle);
        }
    }

    @Builtin(name = "PyTruffle_PtrCache_Create", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTrufflePtrCacheCreate extends PythonUnaryBuiltinNode {
        @Specialization
        static Object createCache(int steal) {
            return new NativeReferenceCache(steal != 0);
        }
    }

    @Builtin(name = "PyTruffle_Decorate_Function", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleDecorateFunction extends PythonBinaryBuiltinNode {
        @Specialization
        static PyCFunctionDecorator decorate(Object fun0, Object fun1) {
            return new PyCFunctionDecorator(fun0, fun1);
        }
    }

    // directly called without landing function
    @Builtin(name = "PyLong_FromLongLong", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyLongFromLongLong extends PythonBinaryBuiltinNode {
        @Specialization(guards = "signed != 0")
        static Object doSignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeInt(n);
        }

        @Specialization(guards = "signed == 0")
        static Object doUnsignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            if (n < 0) {
                return toNewRefNode.executeLong(n & 0xFFFFFFFFL);
            }
            return toNewRefNode.executeInt(n);
        }

        @Specialization(guards = "signed != 0")
        static Object doSignedLong(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeLong(n);
        }

        @Specialization(guards = {"signed == 0", "n >= 0"})
        static Object doUnsignedLongPositive(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeLong(n);
        }

        @Specialization(guards = {"signed == 0", "n < 0"})
        Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(factory().createInt(convertToBigInteger(n)));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }
    }

    @Builtin(name = "PyLong_FromVoidPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongFromVoidPtr extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        Object doPointer(Object pointer,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @CachedLibrary("pointer") InteropLibrary lib) {
            // We capture the native pointer at the time when we create the wrapper if it exists.
            if (lib.isPointer(pointer)) {
                try {
                    return toSulongNode.execute(factory().createNativeVoidPtr(pointer, lib.asPointer(pointer)));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return toSulongNode.execute(factory().createNativeVoidPtr(pointer));
        }
    }

    @Builtin(name = "PyLong_AsVoidPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyLongAsVoidPtr extends PythonUnaryBuiltinNode {
        @Child private ConvertPIntToPrimitiveNode asPrimitiveNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        public abstract Object execute(Object o);

        @Specialization
        static long doPointer(int n) {
            return n;
        }

        @Specialization
        static long doPointer(long n) {
            return n;
        }

        @Specialization
        long doPointer(PInt n,
                        @Cached BranchProfile overflowProfile) {
            try {
                return n.longValueExact();
            } catch (OverflowException e) {
                overflowProfile.enter();
                try {
                    throw raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
                } catch (PException pe) {
                    ensureTransformExcNode().execute(pe);
                    return 0;
                }
            }
        }

        @Specialization
        static Object doPointer(PythonNativeVoidPtr n) {
            return n.getPointerObject();
        }

        @Fallback
        long doGeneric(Object n) {
            if (asPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPrimitiveNode = insert(ConvertPIntToPrimitiveNodeGen.create());
            }
            try {
                try {
                    return asPrimitiveNode.executeLong(n, 0, Long.BYTES);
                } catch (UnexpectedResultException e) {
                    throw raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
                }
            } catch (PException e) {
                ensureTransformExcNode().execute(e);
                return 0;
            }
        }

        private TransformExceptionToNativeNode ensureTransformExcNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }
    }

    @Builtin(name = "PyType_IsSubtype", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PythonOptions.class)
    abstract static class PyType_IsSubtype extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"a == cachedA", "b == cachedB"}, assumptions = "singleContextAssumption()")
        static int doCached(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PythonNativeWrapper a, @SuppressWarnings("unused") PythonNativeWrapper b,
                        @Cached(value = "a", weak = true) @SuppressWarnings("unused") PythonNativeWrapper cachedA,
                        @Cached(value = "b", weak = true) @SuppressWarnings("unused") PythonNativeWrapper cachedB,
                        @Cached("doSlow(frame, a, b)") int result) {
            return result;
        }

        protected static Class<?> getClazz(Object v) {
            return v.getClass();
        }

        @Specialization(replaces = "doCached", guards = {"cachedClassA == getClazz(a)", "cachedClassB == getClazz(b)"}, limit = "getVariableArgumentInlineCacheLimit()")
        int doCachedClass(VirtualFrame frame, Object a, Object b,
                        @Cached("getClazz(a)") Class<?> cachedClassA,
                        @Cached("getClazz(b)") Class<?> cachedClassB,
                        @Cached ToJavaNode leftToJavaNode,
                        @Cached ToJavaNode rightToJavaNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object ua = leftToJavaNode.execute(cachedClassA.cast(a));
            Object ub = rightToJavaNode.execute(cachedClassB.cast(b));
            return isSubtypeNode.execute(frame, ua, ub) ? 1 : 0;
        }

        @Specialization(replaces = {"doCached", "doCachedClass"})
        int doGeneric(VirtualFrame frame, Object a, Object b,
                        @Cached ToJavaNode leftToJavaNode,
                        @Cached ToJavaNode rightToJavaNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object ua = leftToJavaNode.execute(a);
            Object ub = rightToJavaNode.execute(b);
            return isSubtypeNode.execute(frame, ua, ub) ? 1 : 0;
        }

        int doSlow(VirtualFrame frame, Object derived, Object cls) {
            return doGeneric(frame, derived, cls, ToJavaNodeGen.getUncached(), ToJavaNodeGen.getUncached(), IsSubtypeNodeGen.getUncached());
        }
    }

    @Builtin(name = "PyTuple_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTuple_GetItem extends PythonBinaryBuiltinNode {

        @Specialization
        Object doPTuple(VirtualFrame frame, PTuple tuple, long key,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= lenNode.execute(sequenceStorage)) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
            return getItemNode.execute(frame, sequenceStorage, key);
        }

        @Fallback
        Object doPTuple(Object tuple, @SuppressWarnings("unused") Object key) {
            // TODO(fa) To be absolutely correct, we need to do a 'isinstance' check on the object.
            throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, tuple, tuple);
        }
    }

    @Builtin(name = "PySequence_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PySequence_Check extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(Object object,
                        @Cached PySequenceCheckNode check) {
            return check.execute(object);
        }
    }

    @Builtin(name = "PyBytes_FromStringAndSize", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyBytes_FromStringAndSize extends NativeBuiltin {
        // n.b.: the specializations for PIBytesLike are quite common on
        // managed, when the PySequenceArrayWrapper that we used never went
        // native, and during the upcall to here it was simply unwrapped again
        // with the ToJava (rather than mapped from a native pointer back into a
        // PythonNativeObject)

        @Specialization
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object module, PythonNativeWrapper object, long size,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            byte[] ary = getByteArrayNode.execute(frame, asPythonObjectNode.execute(object));
            PBytes result;
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                result = factory().createBytes(Arrays.copyOf(ary, (int) size));
            } else {
                result = factory().createBytes(ary);
            }
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        Object doNativePointer(VirtualFrame frame, Object module, Object nativePointer, long size,
                        @Exclusive @Cached GetNativeNullNode getNativeNullNode,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            try {
                return toSulongNode.execute(factory().createBytes(getByteArrayNode.execute(nativePointer, size)));
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            } catch (OverflowException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.SystemError, "negative size passed");
            }
        }
    }

    @Builtin(name = "PyFloat_AsDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyFloatAsDouble extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!object.isDouble()")
        static double doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static double doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getDouble();
        }

        @Specialization
        static double doGenericErr(VirtualFrame frame, Object object,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return asDoubleNode.execute(frame, asPythonObjectNode.execute(object));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1.0;
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_Float", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyNumberFloat extends NativeBuiltin {

        @Specialization(guards = "object.isDouble()")
        static Object doDoubleNativeWrapper(@SuppressWarnings("unused") Object module, PrimitiveNativeWrapper object,
                        @Cached AddRefCntNode refCntNode) {
            return refCntNode.inc(object);
        }

        @Specialization(guards = "!object.isDouble()")
        static Object doLongNativeWrapper(@SuppressWarnings("unused") Object module, PrimitiveNativeWrapper object,
                        @Cached ToNewRefNode primitiveToSulongNode) {
            return primitiveToSulongNode.execute((double) object.getLong());
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object module, Object object,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            try {
                return toNewRefNode.execute(pyNumberFloat.execute(frame, asPythonObjectNode.execute(object)));
            } catch (PException e) {
                transformToNative(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "PySet_Add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySet_Add extends PythonBinaryBuiltinNode {

        @Specialization
        int add(VirtualFrame frame, PBaseSet self, Object o,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, self, o, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
            return 0;
        }

        @Specialization(guards = "!isAnySet(self)")
        int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a set object", self);
        }
    }

    @Builtin(name = "_PyBytes_Resize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytes_Resize extends PythonBinaryBuiltinNode {

        @Specialization
        int resize(VirtualFrame frame, PBytes self, long newSizeL,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToByteNode castToByteNode) {

            SequenceStorage storage = self.getSequenceStorage();
            int newSize = asSizeNode.executeExact(frame, newSizeL);
            int len = lenNode.execute(storage);
            byte[] smaller = new byte[newSize];
            for (int i = 0; i < newSize && i < len; i++) {
                smaller[i] = castToByteNode.execute(frame, getItemNode.execute(frame, storage, i));
            }
            self.setSequenceStorage(new ByteSequenceStorage(smaller));
            return 0;
        }

        @Specialization(guards = "!isBytes(self)")
        int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a set object", self);
        }

    }

    @Builtin(name = "PyTruffle_Compute_Mro", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_Compute_Mro extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNativeObject(self)")
        Object doIt(Object self, String className) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(PythonNativeClass.cast(self));
            return factory().createTuple(new MroSequenceStorage(className, doSlowPath));
        }
    }

    @Builtin(name = "PyTruffle_NewTypeDict")
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffleNewTypeDict extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDict doGeneric(PythonNativeClass nativeClass) {
            PythonLanguage language = PythonLanguage.get(null);
            Store nativeTypeStore = new Store(language.getEmptyShape());
            DynamicObjectLibrary.getUncached().put(nativeTypeStore, PythonNativeClass.INSTANCESHAPE, language.getShapeForClass(nativeClass));
            return PythonObjectFactory.getUncached().createDict(new DynamicObjectStorage(nativeTypeStore));
        }
    }

    @Builtin(name = "PyTruffle_Type_Modified", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_Type_Modified extends PythonTernaryBuiltinNode {

        @TruffleBoundary
        @Specialization(guards = {"isNativeClass(clazz)", "isNoValue(mroTuple)"})
        Object doIt(Object clazz, String name, @SuppressWarnings("unused") PNone mroTuple) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption((PythonNativeClass) clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name + "\") (without MRO) called");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return PNone.NONE;
        }

        @TruffleBoundary
        @Specialization(guards = "isNativeClass(clazz)")
        Object doIt(Object clazz, String name, PTuple mroTuple,
                        @Cached("createClassProfile()") ValueProfile profile) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption((PythonNativeClass) clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name + "\") called");
            }
            SequenceStorage sequenceStorage = profile.profile(mroTuple.getSequenceStorage());
            if (sequenceStorage instanceof MroSequenceStorage) {
                ((MroSequenceStorage) sequenceStorage).lookupChanged();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid MRO object for native type \"" + name + "\"");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_FatalError", parameterNames = {"prefix", "msg", "status"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_FatalError extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doStrings(String prefix, String msg, int status) {
            CExtCommonNodes.fatalError(this, PythonContext.get(this), prefix, msg, status);
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        Object doGeneric(Object prefixObj, Object msgObj, int status) {
            String prefix = prefixObj == PNone.NO_VALUE ? null : (String) prefixObj;
            String msg = msgObj == PNone.NO_VALUE ? null : (String) msgObj;
            return doStrings(prefix, msg, status);
        }
    }

    @Builtin(name = "PyUnicode_DecodeUTF8Stateful", minNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyUnicode_DecodeUTF8Stateful extends NativeUnicodeBuiltin {

        @Specialization
        Object doUtf8Decode(VirtualFrame frame, Object module, Object cByteArray, String errors, @SuppressWarnings("unused") int reportConsumed,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached GetNativeNullNode getNativeNullNode) {

            try {
                ByteBuffer inputBuffer = wrap(getByteArrayNode.execute(cByteArray, -1));
                int n = remaining(inputBuffer);
                CharBuffer resultBuffer = allocateCharBuffer(n * 4);
                decodeUTF8(resultBuffer, inputBuffer, errors);
                return toSulongNode.execute(factory().createTuple(new Object[]{toString(resultBuffer), n - remaining(inputBuffer)}));
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            } catch (OverflowException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.SystemError, ErrorMessages.INPUT_TOO_LONG);
            }
        }

        @TruffleBoundary
        private void decodeUTF8(CharBuffer resultBuffer, ByteBuffer inputBuffer, String errors) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
        }
    }

    @Builtin(name = "PyTruffle_OS_StringToDouble", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyTruffle_OS_StringToDouble extends NativeBuiltin {

        @Specialization
        Object doGeneric(VirtualFrame frame, Object module, String source, int reportPos,
                        @Cached GetNativeNullNode getNativeNullNode) {

            if (reportPos != 0) {
                ParsePosition pp = new ParsePosition(0);
                Number parse = parse(source, pp);
                if (parse != null) {
                    return factory().createTuple(new Object[]{doubleValue(parse), pp.getIndex()});
                }
            } else {
                try {
                    Number parse = parse(source);
                    return factory().createTuple(new Object[]{doubleValue(parse)});
                } catch (ParseException e) {
                    // ignore
                }
            }
            return raiseNative(frame, getNativeNullNode.execute(module), PythonBuiltinClassType.ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_FLOAT, source);
        }

        @TruffleBoundary
        private static double doubleValue(Number parse) {
            return parse.doubleValue();
        }

        @TruffleBoundary
        private static Number parse(String source, ParsePosition pp) {
            return DecimalFormat.getInstance().parse(source, pp);
        }

        @TruffleBoundary
        private static Number parse(String source) throws ParseException {
            return DecimalFormat.getInstance().parse(source);
        }
    }

    @Builtin(name = "PyTruffle_OS_DoubleToString", minNumOfPositionalArgs = 5, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyTruffle_OS_DoubleToString extends NativeBuiltin {

        /* keep in sync with macro 'TRANSLATE_TYPE' in 'pystrtod.c' */
        private static final int Py_DTST_FINITE = 0;
        private static final int Py_DTST_INFINITE = 1;
        private static final int Py_DTST_NAN = 2;

        @Specialization(guards = "isReprFormatCode(formatCode)")
        @SuppressWarnings("unused")
        PTuple doRepr(VirtualFrame frame, Object module, double val, int formatCode, int precision, int flags,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode callReprNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            Object reprString = callReprNode.executeObject(frame, val);
            try {
                return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "!isReprFormatCode(formatCode)")
        Object doGeneric(VirtualFrame frame, Object module, double val, int formatCode, int precision, @SuppressWarnings("unused") int flags,
                        @Cached("create(__FORMAT__)") LookupAndCallBinaryNode callReprNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object reprString = callReprNode.executeObject(frame, val, joinFormatCode(formatCode, precision));
                try {
                    return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            } catch (PException e) {
                transformToNative(frame, e);
                return getNativeNullNode.execute(module);
            }
        }

        @TruffleBoundary
        private static String joinFormatCode(int formatCode, int precision) {
            return "." + precision + (char) formatCode;
        }

        private PTuple createResult(Object str, double val) {
            return factory().createTuple(new Object[]{str, getTypeCode(val)});
        }

        private static int getTypeCode(double val) {
            if (Double.isInfinite(val)) {
                return Py_DTST_INFINITE;
            } else if (Double.isNaN(val)) {
                return Py_DTST_NAN;
            }
            assert Double.isFinite(val);
            return Py_DTST_FINITE;
        }

        protected static boolean isReprFormatCode(int formatCode) {
            return (char) formatCode == 'r';
        }
    }

    @Builtin(name = "PyUnicode_Decode", minNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyUnicode_Decode extends NativeBuiltin {

        @Specialization
        Object doDecode(VirtualFrame frame, Object module, PMemoryView mv, String encoding, String errors,
                        @Cached CodecsModuleBuiltins.DecodeNode decodeNode,
                        @Cached CExtNodes.ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(decodeNode.executeWithStrings(frame, mv, encoding, errors));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toSulongNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "PyObject_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyObject_Size extends PythonUnaryBuiltinNode {

        // n.b.: specializations 'doSequence' and 'doMapping' are not just shortcuts but also
        // required for correctness because CPython's implementation uses
        // 'type->tp_as_sequence->sq_length', 'type->tp_as_mapping->mp_length' which will bypass
        // any
        // user implementation of '__len__'.
        @Specialization
        static int doSequence(PSequence sequence,
                        @Cached SequenceNodes.LenNode seqLenNode) {
            return seqLenNode.execute(sequence);
        }

        @Specialization
        static int doMapping(PHashingCollection container,
                        @Cached HashingCollectionNodes.LenNode seqLenNode) {
            return seqLenNode.execute(container);
        }

        @Specialization(guards = "!isMappingOrSequence(obj)")
        static Object doGenericUnboxed(VirtualFrame frame, Object obj,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLenNode,
                        @Cached ConditionProfile noLenProfile,
                        @Cached CastToNativeLongNode castToLongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = callLenNode.executeObject(frame, obj);
                if (noLenProfile.profile(result == PNone.NO_VALUE)) {
                    return -1;
                }
                Object lresult = castToLongNode.execute(result);
                assert lresult instanceof Long || lresult instanceof PythonNativeVoidPtr;
                return lresult;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        protected static boolean isMappingOrSequence(Object obj) {
            return obj instanceof PSequence || obj instanceof PHashingCollection;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_Call", parameterNames = {"callee", "args", "kwargs", "single_arg"})
    @GenerateNodeFactory
    abstract static class PyObjectCallNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object callableObj, Object argsObj, Object kwargsObj, int singleArg,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CastArgsNode castArgsNode,
                        @Cached CastKwargsNode castKwargsNode,
                        @Cached CallNode callNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object callable = asPythonObjectNode.execute(callableObj);
                Object[] args;
                if (singleArg != 0) {
                    args = new Object[]{asPythonObjectNode.execute(argsObj)};
                } else {
                    args = castArgsNode.execute(frame, argsObj);
                }
                PKeyword[] keywords = castKwargsNode.execute(kwargsObj);
                return toNewRefNode.execute(callNode.execute(frame, callable, args, keywords));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(getNativeNullNode.execute());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_CallFunctionObjArgs", parameterNames = {"callable", "va_list"})
    @GenerateNodeFactory
    abstract static class PyObjectCallFunctionObjArgsNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        static Object doFunction(VirtualFrame frame, Object callableObj, Object vaList,
                        @CachedLibrary("vaList") InteropLibrary argsArrayLib,
                        @Shared("argLib") @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Cached CallNode callNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached GetLLVMType getLLVMType,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object callable = asPythonObjectNode.execute(callableObj);
                return toNewRefNode.execute(callFunction(frame, callable, vaList, argsArrayLib, argLib, callNode, toJavaNode, getLLVMType));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(getNativeNullNode.execute());
            }
        }

        static Object callFunction(VirtualFrame frame, Object callable, Object vaList,
                        InteropLibrary argsArrayLib,
                        InteropLibrary argLib,
                        CallNode callNode,
                        CExtNodes.ToJavaNode toJavaNode,
                        GetLLVMType getLLVMType) {
            if (argsArrayLib.hasArrayElements(vaList)) {
                try {
                    /*
                     * Function 'PyObject_CallFunctionObjArgs' expects a va_list that contains just
                     * 'PyObject *' and is terminated by 'NULL'. Hence, we allocate an argument
                     * array with one element less than the va_list object says (since the last
                     * element is expected to be 'NULL'; this is best effort). However, we must also
                     * stop at the first 'NULL' element we encounter since a user could pass several
                     * 'NULL'.
                     */
                    long arraySize = argsArrayLib.getArraySize(vaList);
                    Object[] args = new Object[PInt.intValueExact(arraySize) - 1];
                    int filled = 0;
                    Object llvmPyObjectPtrType = getLLVMType.execute(LLVMType.PyObject_ptr_t);
                    for (int i = 0; i < args.length; i++) {
                        try {
                            Object object = argsArrayLib.invokeMember(vaList, "get", i, llvmPyObjectPtrType);
                            if (argLib.isNull(object)) {
                                break;
                            }
                            args[i] = toJavaNode.execute(object);
                            filled++;
                        } catch (ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                            throw CompilerDirectives.shouldNotReachHere();
                        }
                    }
                    if (filled < args.length) {
                        args = PythonUtils.arrayCopyOf(args, filled);
                    }
                    return callNode.execute(frame, callable, args);
                } catch (UnsupportedMessageException | OverflowException e) {
                    // I think we can just assume that there won't be more than
                    // Integer.MAX_VALUE arguments.
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_CallMethodObjArgs", parameterNames = {"receiver", "method_name", "va_list"})
    @GenerateNodeFactory
    abstract static class PyObjectCallMethodObjArgsNode extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        static Object doMethod(VirtualFrame frame, Object receiverObj, Object methodNameObj, Object vaList,
                        @CachedLibrary("vaList") InteropLibrary argsArrayLib,
                        @Shared("argLib") @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Cached CallNode callNode,
                        @Cached GetAnyAttributeNode getAnyAttributeNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached GetLLVMType getLLVMType,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object receiver = asPythonObjectNode.execute(receiverObj);
                Object methodName = asPythonObjectNode.execute(methodNameObj);
                Object method = getAnyAttributeNode.executeObject(frame, receiver, methodName);
                return toNewRefNode.execute(PyObjectCallFunctionObjArgsNode.callFunction(frame, method, vaList, argsArrayLib, argLib, callNode, toJavaNode, getLLVMType));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(getNativeNullNode.execute());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_CallMethod", parameterNames = {"object", "method_name", "args", "single_arg"})
    @GenerateNodeFactory
    abstract static class PyObjectCallMethodNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object receiverObj, String methodName, Object argsObj, int singleArg,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CastArgsNode castArgsNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object receiver = asPythonObjectNode.execute(receiverObj);
                Object[] args;
                if (singleArg != 0) {
                    args = new Object[]{asPythonObjectNode.execute(argsObj)};
                } else {
                    args = castArgsNode.execute(frame, argsObj);
                }
                return toNewRefNode.execute(callMethod.execute(frame, receiver, methodName, args));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(getNativeNullNode.execute());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_FastCallDict", parameterNames = {"callable", "argsArray", "kwargs"})
    @GenerateNodeFactory
    abstract static class PyObjectFastCallDictNode extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        static Object doGeneric(VirtualFrame frame, Object callableObj, Object argsArray, Object kwargsObj,
                        @CachedLibrary("argsArray") InteropLibrary argsArrayLib,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CastKwargsNode castKwargsNode,
                        @Cached CallNode callNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            if (argsArrayLib.hasArrayElements(argsArray)) {
                try {
                    try {
                        // consume all arguments of the va_list
                        long arraySize = argsArrayLib.getArraySize(argsArray);
                        Object[] args = new Object[PInt.intValueExact(arraySize)];
                        for (int i = 0; i < args.length; i++) {
                            try {
                                args[i] = toJavaNode.execute(argsArrayLib.readArrayElement(argsArray, i));
                            } catch (InvalidArrayIndexException e) {
                                throw CompilerDirectives.shouldNotReachHere();
                            }
                        }
                        Object callable = asPythonObjectNode.execute(callableObj);
                        PKeyword[] keywords = castKwargsNode.execute(kwargsObj);
                        return toNewRefNode.execute(callNode.execute(frame, callable, args, keywords));
                    } catch (UnsupportedMessageException | OverflowException e) {
                        // I think we can just assume that there won't be more than
                        // Integer.MAX_VALUE arguments.
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                } catch (PException e) {
                    // transformExceptionToNativeNode acts as a branch profile
                    transformExceptionToNativeNode.execute(frame, e);
                    return nullToSulongNode.execute(getNativeNullNode.execute());
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @ReportPolymorphism
    abstract static class CastArgsNode extends Node {

        public abstract Object[] execute(VirtualFrame frame, Object argsObj);

        @Specialization(guards = "lib.isNull(argsObj)")
        @SuppressWarnings("unused")
        static Object[] doNull(VirtualFrame frame, Object argsObj,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return EMPTY_OBJECT_ARRAY;
        }

        @Specialization(guards = "!lib.isNull(argsObj)")
        static Object[] doNotNull(VirtualFrame frame, Object argsObj,
                        @Cached ExecutePositionalStarargsNode expandArgsNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("lib") @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib) {
            return expandArgsNode.executeWith(frame, asPythonObjectNode.execute(argsObj));
        }
    }

    @ReportPolymorphism
    abstract static class CastKwargsNode extends Node {

        public abstract PKeyword[] execute(Object kwargsObj);

        @Specialization(guards = "lib.isNull(kwargsObj) || isEmptyDict(kwargsToJavaNode, lenNode, kwargsObj)", limit = "1")
        @SuppressWarnings("unused")
        static PKeyword[] doNoKeywords(Object kwargsObj,
                        @Shared("lenNode") @Cached HashingCollectionNodes.LenNode lenNode,
                        @Shared("kwargsToJavaNode") @Cached AsPythonObjectNode kwargsToJavaNode,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = {"!lib.isNull(kwargsObj)", "!isEmptyDict(kwargsToJavaNode, lenNode, kwargsObj)"}, limit = "1")
        static PKeyword[] doKeywords(Object kwargsObj,
                        @Shared("lenNode") @Cached @SuppressWarnings("unused") HashingCollectionNodes.LenNode lenNode,
                        @Shared("kwargsToJavaNode") @Cached AsPythonObjectNode kwargsToJavaNode,
                        @Shared("lib") @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode) {
            return expandKwargsNode.execute(kwargsToJavaNode.execute(kwargsObj));
        }

        static boolean isEmptyDict(AsPythonObjectNode asPythonObjectNode, HashingCollectionNodes.LenNode lenNode, Object kwargsObj) {
            Object unwrapped = asPythonObjectNode.execute(kwargsObj);
            if (unwrapped instanceof PDict) {
                return lenNode.execute((PDict) unwrapped) == 0;
            }
            return false;
        }
    }

    public abstract static class ParseTupleAndKeywordsBaseNode extends PythonVarargsBuiltinNode {

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, self, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public static int doConvert(CExtContext nativeContext, Object argv, Object nativeKwds, Object nativeFormat, Object nativeKwdnames, Object nativeVarargs,
                        SplitFormatStringNode splitFormatStringNode,
                        InteropLibrary kwdsRefLib,
                        InteropLibrary kwdnamesRefLib,
                        ConditionProfile kwdsProfile,
                        ConditionProfile kwdnamesProfile,
                        CExtAsPythonObjectNode kwdsToJavaNode,
                        CastToJavaStringNode castToStringNode,
                        CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {

            // force 'format' to be a String
            String[] split;
            try {
                split = splitFormatStringNode.execute(castToStringNode.execute(nativeFormat));
                assert split.length == 2;
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }

            String format = split[0];
            String functionName = split[1];

            // sort out if kwds is native NULL
            Object kwds;
            if (kwdsProfile.profile(kwdsRefLib.isNull(nativeKwds))) {
                kwds = null;
            } else {
                kwds = kwdsToJavaNode.execute(nativeContext, nativeKwds);
            }

            // sort out if kwdnames is native NULL
            Object kwdnames = kwdnamesProfile.profile(kwdnamesRefLib.isNull(nativeKwdnames)) ? null : nativeKwdnames;

            return parseTupleAndKeywordsNode.execute(functionName, argv, kwds, format, kwdnames, nativeVarargs, nativeContext);
        }

        static Object getKwds(Object[] arguments) {
            return arguments[1];
        }

        static Object getKwdnames(Object[] arguments) {
            return arguments[3];
        }
    }

    @Builtin(name = "PyTruffle_Arg_ParseTupleAndKeywords", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ParseTupleAndKeywordsNode extends ParseTupleAndKeywordsBaseNode {

        @Specialization(guards = "arguments.length == 5", limit = "2")
        static int doConvert(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached SplitFormatStringNode splitFormatStringNode,
                        @CachedLibrary("getKwds(arguments)") InteropLibrary kwdsInteropLib,
                        @CachedLibrary("getKwdnames(arguments)") InteropLibrary kwdnamesRefLib,
                        @Cached ConditionProfile kwdsProfile,
                        @Cached ConditionProfile kwdnamesProfile,
                        @Cached AsPythonObjectNode argvToJavaNode,
                        @Cached AsPythonObjectNode kwdsToJavaNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {
            CExtContext nativeContext = PythonContext.get(splitFormatStringNode).getCApiContext();
            Object argv = argvToJavaNode.execute(arguments[0]);
            return ParseTupleAndKeywordsBaseNode.doConvert(nativeContext, argv, arguments[1], arguments[2], arguments[3], arguments[4], splitFormatStringNode, kwdsInteropLib, kwdnamesRefLib,
                            kwdsProfile, kwdnamesProfile, kwdsToJavaNode, castToStringNode, parseTupleAndKeywordsNode);
        }

    }

    @Builtin(name = "PyTruffle_Create_Lightweight_Upcall", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleCreateLightweightUpcall extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(String key) {
            switch (key) {
                case "PyTruffle_Object_Alloc":
                    return new PyTruffleObjectAlloc();
                case "PyTruffle_Object_Free":
                    return new PyTruffleObjectFree();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("");
        }
    }

    @Builtin(name = "PyTruffle_TraceMalloc_Track", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleTraceMallocTrack extends PythonBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceMallocTrack.class);

        @Specialization(guards = {"domain == cachedDomain"}, limit = "3", assumptions = "singleContextAssumption()")
        int doCachedDomainIdx(VirtualFrame frame, @SuppressWarnings("unused") long domain, Object pointerObject, long size,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            CApiContext cApiContext = getContext().getCApiContext();
            cApiContext.getTraceMallocDomain(cachedDomainIdx).track(pointerObject, size);
            cApiContext.increaseMemoryPressure(frame, getThreadStateNode, this, size);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> String.format("Tracking memory (size: %d): %s", size, CApiContext.asHex(pointerObject)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(VirtualFrame frame, long domain, Object pointerObject, long size,
                        @Cached GetThreadStateNode getThreadStateNode) {
            return doCachedDomainIdx(frame, domain, pointerObject, size, getThreadStateNode, domain, lookupDomain(domain));
        }

        int lookupDomain(long domain) {
            return getContext().getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @Builtin(name = "PyTruffle_TraceMalloc_Untrack", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleTraceMallocUntrack extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceMallocUntrack.class);

        @Specialization(guards = {"domain == cachedDomain"}, limit = "3", assumptions = "singleContextAssumption()")
        int doCachedDomainIdx(@SuppressWarnings("unused") long domain, Object pointerObject,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            CApiContext cApiContext = getContext().getCApiContext();
            long trackedMemorySize = cApiContext.getTraceMallocDomain(cachedDomainIdx).untrack(pointerObject);
            cApiContext.reduceMemoryPressure(trackedMemorySize);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> String.format("Untracking memory (size: %d): %s", trackedMemorySize, CApiContext.asHex(pointerObject)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(long domain, Object pointerObject) {
            return doCachedDomainIdx(domain, pointerObject, domain, lookupDomain(domain));
        }

        int lookupDomain(long domain) {
            return getContext().getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @Builtin(name = "PyTruffle_TraceMalloc_NewReference", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceMallocNewReference extends PythonUnaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static int doCachedDomainIdx(Object pointerObject) {
            // TODO(fa): implement; capture tracebacks in PyTraceMalloc_Track and update them
            // here
            return 0;
        }
    }

    abstract static class PyTruffleGcTracingNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!traceCalls(getContext())", "traceMem(getContext())"})
        int doNativeWrapper(Object ptr,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            trace(getContext(), CApiContext.asPointer(ptr, lib), null, null);
            return 0;
        }

        @Specialization(guards = {"traceCalls(getContext())", "traceMem(getContext())"})
        int doNativeWrapperTraceCall(VirtualFrame frame, Object ptr,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {

            PFrame.Reference ref = getCurrentFrameRef.execute(frame);
            trace(getContext(), CApiContext.asPointer(ptr, lib), ref, null);
            return 0;
        }

        @Specialization(guards = "!traceMem(getContext())")
        static int doNothing(@SuppressWarnings("unused") Object ptr) {
            // do nothing
            return 0;
        }

        static boolean traceMem(PythonContext context) {
            return context.getOption(PythonOptions.TraceNativeMemory);
        }

        static boolean traceCalls(PythonContext context) {
            return context.getOption(PythonOptions.TraceNativeMemoryCalls);
        }

        @SuppressWarnings("unused")
        protected void trace(PythonContext context, Object ptr, Reference ref, String className) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("should not reach");
        }
    }

    @Builtin(name = "PyTruffle_GC_Untrack", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGcUntrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleGcUntrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, String className) {
            LOGGER.fine(() -> String.format("Untracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().untrackObject(ptr, ref, className);
        }
    }

    @Builtin(name = "PyTruffle_GC_Track", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGcTrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleGcTrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, String className) {
            LOGGER.fine(() -> String.format("Tracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().trackObject(ptr, ref, className);
        }
    }

    @Builtin(name = "PyTruffle_Native_Options")
    @GenerateNodeFactory
    abstract static class PyTruffleNativeOptions extends PythonBuiltinNode {
        private static final int TRACE_MEM = 0x1;

        @Specialization
        int getNativeOptions() {
            int options = 0;
            if (getContext().getOption(PythonOptions.TraceNativeMemory)) {
                options |= TRACE_MEM;
            }
            return options;
        }
    }

    /**
     * This will be called right before the call to stdlib's {@code free} function.
     */
    @Builtin(name = "PyTruffle_Trace_Free", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceFree extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceFree.class);

        @Specialization(limit = "2")
        static int doNativeWrapperLong(Object ptr, long size,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {

            PythonContext context = PythonContext.get(lib);
            CApiContext cApiContext = context.getCApiContext();
            cApiContext.reduceMemoryPressure(size);

            boolean isLoggable = LOGGER.isLoggable(Level.FINER);
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            if ((isLoggable || traceNativeMemory) && !lib.isNull(ptr)) {
                boolean traceNativeMemoryCalls = context.getOption(PythonOptions.TraceNativeMemoryCalls);
                if (traceNativeMemory) {
                    PFrame.Reference ref = null;
                    if (traceNativeMemoryCalls) {
                        ref = getCurrentFrameRef.execute(null);
                    }
                    AllocInfo allocLocation = cApiContext.traceFree(CApiContext.asPointer(ptr, lib), ref, null);
                    if (allocLocation != null) {
                        LOGGER.finer(() -> String.format("Freeing pointer (size: %d): %s", allocLocation.size, CApiContext.asHex(ptr)));

                        if (traceNativeMemoryCalls) {
                            Reference left = allocLocation.allocationSite;
                            PFrame pyFrame = null;
                            while (pyFrame == null && left != null) {
                                pyFrame = left.getPyFrame();
                                left = left.getCallerInfo();
                            }
                            if (pyFrame != null) {
                                final PFrame f = pyFrame;
                                LOGGER.finer(() -> String.format("Free'd pointer was allocated at: %s", f.getTarget()));
                            }
                        }
                    }
                } else {
                    assert isLoggable;
                    LOGGER.finer(() -> String.format("Freeing pointer: %s", CApiContext.asHex(ptr)));
                }
            }
            return 0;
        }

        @Specialization(limit = "2", replaces = "doNativeWrapperLong")
        static int doNativeWrapper(Object ptr, Object sizeObject,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {
            long size;
            try {
                size = castToJavaLongNode.execute(sizeObject);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("invalid type for second argument 'objectSize'");
            }
            return doNativeWrapperLong(ptr, size, lib, getCurrentFrameRef);
        }

    }

    @Builtin(name = "PyTruffle_Trace_Type", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceType extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceType.class);

        @Specialization(limit = "3")
        int trace(Object ptr, Object classNameObj,
                        @CachedLibrary("ptr") InteropLibrary ptrLib,
                        @CachedLibrary("classNameObj") InteropLibrary nameLib) {
            final String className;
            if (nameLib.isString(classNameObj)) {
                try {
                    className = nameLib.asString(classNameObj);
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
            LOGGER.fine(() -> String.format("Initializing native type %s (ptr = %s)", className, CApiContext.asHex(primitivePtr)));
            return 0;
        }
    }

    @Builtin(name = "PyList_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyListSetItem extends PythonTernaryBuiltinNode {
        @Specialization
        int doManaged(VirtualFrame frame, PythonNativeWrapper listWrapper, Object position, Object elementWrapper,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectStealingNode elementAsPythonObjectNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                if (!PGuards.isList(delegate)) {
                    throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, delegate, delegate);
                }
                PList list = (PList) delegate;
                Object element = elementAsPythonObjectNode.execute(elementWrapper);
                setItemNode.execute(frame, list.getSequenceStorage(), position, element);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forListAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "PyList_Reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyListReverse extends PythonUnaryBuiltinNode {
        @Specialization
        int reverse(VirtualFrame frame, PList self,
                        @Cached ListBuiltins.ListReverseNode reverseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                reverseNode.execute(frame, self);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PySequence_GetItem", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PySequenceGetItem extends PythonTernaryBuiltinNode {

        @Specialization
        Object doManaged(VirtualFrame frame, Object module, Object listWrapper, Object position,
                        @Cached PySequenceCheckNode pySequenceCheck,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getItem,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode positionAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                if (!pySequenceCheck.execute(delegate)) {
                    throw raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_INDEXING, delegate);
                }
                Object item = getItem.execute(frame, delegate, positionAsPythonObjectNode.execute(position));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "PyObject_GetItem", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyObjectGetItem extends PythonTernaryBuiltinNode {
        @Specialization
        Object doManaged(VirtualFrame frame, Object module, Object listWrapper, Object key,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getItem,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode keyAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                Object item = getItem.execute(frame, delegate, keyAsPythonObjectNode.execute(key));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "wrap_PyDateTime_CAPI", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WrapPyDateTimeCAPI extends PythonBuiltinNode {
        @Specialization
        static Object doGeneric(Object object) {
            return new PyDateTimeCAPIWrapper(object);
        }
    }

    @Builtin(name = "_PyModule_GetAndIncMaxModuleNumber")
    @GenerateNodeFactory
    abstract static class PyModuleGetAndIncMaxModuleNumber extends PythonBuiltinNode {

        @Specialization
        long doIt() {
            CApiContext nativeContext = getContext().getCApiContext();
            return nativeContext.getAndIncMaxModuleNumber();
        }
    }

    @ImportStatic(CExtContext.class)
    abstract static class NewClassMethodNode extends Node {

        abstract Object execute(String name, Object methObj, Object flags, Object wrapper, Object type, Object doc,
                        PythonObjectFactory factory);

        @Specialization(guards = "isClassOrStaticMethod(flags)")
        static Object classOrStatic(String name, Object methObj, int flags, int wrapper, Object type,
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
            dylib.put(function, __NAME__, name);
            dylib.put(function, __DOC__, cstrPtr.execute(doc));
            return function;
        }

        @Specialization(guards = "!isClassOrStaticMethod(flags)")
        static Object doNativeCallable(String name, Object methObj, int flags, int wrapper, Object type,
                        Object doc, PythonObjectFactory factory,
                        @Cached PyObject_Setattr setattr,
                        @Shared("cf") @Cached CreateFunctionNode createFunctionNode,
                        @Shared("cstr") @Cached CharPtrToJavaObjectNode cstrPtr) {
            Object func = createFunctionNode.execute(name, methObj, wrapper, type, flags, factory);
            setattr.execute(func, __NAME__, name);
            setattr.execute(func, __DOC__, cstrPtr.execute(doc));
            return func;
        }
    }

    // directly called without landing function
    @Builtin(name = "AddFunction", minNumOfPositionalArgs = 6, parameterNames = {"primary", "tpDict", "name", "cfunc", "flags", "wrapper", "doc"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "wrapper", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class AddFunctionNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.AddFunctionNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "isPythonModule(owner)")
        Object moduleFunction(VirtualFrame frame, @SuppressWarnings("unused") Object primary,
                        @SuppressWarnings("unused") Object tpDict,
                        String name, Object cfunc, int flags, int wrapper, Object doc,
                        @SuppressWarnings("unused") @Cached AsPythonObjectNode asPythonObjectNode,
                        @Bind("getOwner(asPythonObjectNode, primary)") Object owner,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode) {
            PythonModule mod = (PythonModule) owner;
            Object modName = dylib.getOrDefault(mod.getStorage(), __NAME__, null);
            assert modName != null : "module name is missing!";
            Object func = cFunctionNewExMethodNode.execute(name, cfunc, flags, wrapper, mod, modName, doc, factory());
            setattrNode.execute(frame, mod, name, func);
            return 0;
        }

        @Specialization(guards = "!isPythonModule(owner)")
        Object classMethod(VirtualFrame frame, @SuppressWarnings("unused") Object primary,
                        Object tpDict, String name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Bind("getOwner(asPythonObjectNode, primary)") Object owner,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached DictBuiltins.SetItemNode setItemNode) {
            Object func = newClassMethodNode.execute(name, cfunc, flags, wrapper, owner, doc, factory());
            Object dict = asPythonObjectNode.execute(tpDict);
            setItemNode.execute(frame, dict, name, func);
            return 0;
        }

        static Object getOwner(AsPythonObjectNode asPythonObjectNode, Object primary) {
            return asPythonObjectNode.execute(primary);
        }
    }

    /**
     * Signature: {@code add_slot(primary, tpDict, name", cfunc, flags, wrapper, doc)}
     */
    // directly called without landing function
    @Builtin(name = "add_slot", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class AddSlotNode extends PythonVarargsBuiltinNode {

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return execute(frame, self, arguments, keywords);
        }

        @Specialization
        int doWithPrimitives(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (arguments.length != 7) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "add_slot", 7, arguments.length);
                }
                addSlot(arguments[0], arguments[1], arguments[2], arguments[3], castInt(arguments[4]), castInt(arguments[5]), arguments[6],
                                AsPythonObjectNodeGen.getUncached(), CastToJavaStringNode.getUncached(), FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached(),
                                CreateFunctionNodeGen.getUncached(), WriteAttributeToDynamicObjectNode.getUncached(), HashingStorageLibrary.getUncached());
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @TruffleBoundary
        private static void addSlot(Object clsPtr, Object tpDictPtr, Object namePtr, Object cfunc, int flags, int wrapper, Object docPtr,
                        AsPythonObjectNode asPythonObjectNode,
                        CastToJavaStringNode castToJavaStringNode,
                        FromCharPointerNode fromCharPointerNode,
                        InteropLibrary docPtrLib,
                        CreateFunctionNode createFunctionNode,
                        WriteAttributeToDynamicObjectNode writeDocNode,
                        HashingStorageLibrary dictStorageLib) {
            Object clazz = asPythonObjectNode.execute(clsPtr);
            PDict tpDict = castPDict(asPythonObjectNode.execute(tpDictPtr));

            String memberName;
            try {
                memberName = castToJavaStringNode.execute(asPythonObjectNode.execute(namePtr));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
            }
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, fromCharPointerNode, docPtrLib);

            // create wrapper descriptor
            Object wrapperDescriptor = createFunctionNode.execute(memberName, cfunc, wrapper, clazz, flags, PythonObjectFactory.getUncached());
            writeDocNode.execute(wrapperDescriptor, SpecialAttributeNames.__DOC__, memberDoc);

            // add wrapper descriptor to tp_dict
            HashingStorage dictStorage = tpDict.getDictStorage();
            HashingStorage updatedStorage = dictStorageLib.setItem(dictStorage, memberName, wrapperDescriptor);
            if (dictStorage != updatedStorage) {
                tpDict.setDictStorage(updatedStorage);
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyDescr_NewClassMethod", minNumOfPositionalArgs = 6, parameterNames = {"name", "doc", "flags", "wrapper", "cfunc", "primary"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class PyDescrNewClassMethod extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.PyDescrNewClassMethodClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doNativeCallable(String name, Object doc, int flags, Object wrapper, Object methObj, Object primary,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached ToNewRefNode newRefNode) {
            Object type = asPythonObjectNode.execute(primary);
            Object func = newClassMethodNode.execute(name, methObj, flags, wrapper, type, doc, factory());
            if (!isClassOrStaticMethod(flags)) {
                /*
                 * NewClassMethodNode only wraps method with METH_CLASS and METH_STATIC set but we
                 * need to do so here.
                 */
                func = factory().createClassmethodFromCallableObj(func);
            }
            return newRefNode.execute(func);
        }
    }

    abstract static class CFunctionNewExMethodNode extends Node {

        abstract Object execute(String name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object doc,
                        PythonObjectFactory factory);

        @Specialization
        static Object doNativeCallable(String name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object doc,
                        PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CreateFunctionNode createFunctionNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CharPtrToJavaObjectNode charPtrToJavaObjectNode) {
            Object f = createFunctionNode.execute(name, methObj, wrapper, PNone.NO_VALUE, flags, factory);
            assert f instanceof PBuiltinFunction;
            PBuiltinFunction func = (PBuiltinFunction) f;
            dylib.put(func.getStorage(), __NAME__, name);
            Object strDoc = charPtrToJavaObjectNode.execute(doc);
            dylib.put(func.getStorage(), __DOC__, strDoc);
            PBuiltinMethod method = factory.createBuiltinMethod(self, func);
            dylib.put(method.getStorage(), __MODULE__, module);
            return method;
        }
    }

    @Builtin(name = "PyCFunction_NewEx", minNumOfPositionalArgs = 7, parameterNames = {"name", "cfunc", "flags", "wrapper", "self", "module", "doc"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class PyCFunctionNewExMethod extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.PyCFunctionNewExMethodClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doNativeCallable(String name, Object methObj, int flags, int wrapper, Object selfO, Object moduleO, Object doc,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode,
                        @Cached ToNewRefNode newRefNode) {
            Object self = asPythonObjectNode.execute(selfO);
            Object module = asPythonObjectNode.execute(moduleO);
            Object func = cFunctionNewExMethodNode.execute(name, methObj, flags, wrapper, self, module, doc, factory());
            return newRefNode.execute(func);
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Unicode_FromFormat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeFromFromat extends PythonBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, String format, Object vaList,
                        @Cached UnicodeFromFormatNode unicodeFromFormatNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(unicodeFromFormatNode.execute(format, vaList));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_CheckEmbeddedNull", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleBytesCheckEmbeddedNull extends PythonUnaryBuiltinNode {

        @Specialization
        static int doBytes(PBytes bytes,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached GetItemScalarNode getItemScalarNode) {

            SequenceStorage sequenceStorage = bytes.getSequenceStorage();
            int len = lenNode.execute(sequenceStorage);
            try {
                for (int i = 0; i < len; i++) {
                    if (getItemScalarNode.executeInt(sequenceStorage, i) == 0) {
                        return -1;
                    }
                }
            } catch (ClassCastException e) {
                throw CompilerDirectives.shouldNotReachHere("bytes object contains non-int value");
            }
            return 0;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_UnaryOp", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberUnaryOp extends PythonBinaryBuiltinNode {
        static int MAX_CACHE_SIZE = UnaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        static Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(callNode.execute(frame, left.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        static Object doObject(VirtualFrame frame, Object left, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                Object leftValue;
                if (left instanceof PrimitiveNativeWrapper) {
                    leftValue = PyNumberBinOp.extract((PrimitiveNativeWrapper) left);
                } else {
                    leftValue = leftToJava.execute(left);
                }
                result = callNode.execute(frame, leftValue);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getNativeNullNode.execute();
            }
            return toSulongNode.execute(result);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_unaryop}.
         */
        static UnaryOpNode createCallNode(int op) {
            UnaryArithmetic unaryArithmetic;
            switch (op) {
                case 0:
                    unaryArithmetic = UnaryArithmetic.Pos;
                    break;
                case 1:
                    unaryArithmetic = UnaryArithmetic.Neg;
                    break;
                case 2:
                    unaryArithmetic = UnaryArithmetic.Invert;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid unary operator");
            }
            return unaryArithmetic.create();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_BinOp", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyNumberBinOp extends PythonTernaryBuiltinNode {
        static int MAX_CACHE_SIZE = BinaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()", "right.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        static Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, PrimitiveNativeWrapper right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(callNode.executeObject(frame, left.getLong(), right.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        static Object doObject(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached AsPythonObjectNode rightToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                if (left instanceof PrimitiveNativeWrapper || right instanceof PrimitiveNativeWrapper) {
                    Object leftValue;
                    Object rightValue;
                    if (left instanceof PrimitiveNativeWrapper) {
                        leftValue = extract((PrimitiveNativeWrapper) left);
                    } else {
                        leftValue = leftToJava.execute(left);
                    }
                    if (right instanceof PrimitiveNativeWrapper) {
                        rightValue = extract((PrimitiveNativeWrapper) right);
                    } else {
                        rightValue = rightToJava.execute(right);
                    }
                    result = callNode.executeObject(frame, leftValue, rightValue);
                } else {
                    result = callNode.executeObject(frame, leftToJava.execute(left), rightToJava.execute(right));
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getNativeNullNode.execute();
            }
            return toSulongNode.execute(result);
        }

        static Object extract(PrimitiveNativeWrapper wrapper) {
            if (wrapper.isIntLike()) {
                return wrapper.getLong();
            }
            if (wrapper.isDouble()) {
                return wrapper.getDouble();
            }
            if (wrapper.isBool()) {
                return wrapper.getBool();
            }
            throw CompilerDirectives.shouldNotReachHere("unexpected wrapper state");
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_binop}.
         */
        static BinaryOpNode createCallNode(int op) {
            return getBinaryArithmetic(op).create();
        }

        private static BinaryArithmetic getBinaryArithmetic(int op) {
            switch (op) {
                case 0:
                    return BinaryArithmetic.Add;
                case 1:
                    return BinaryArithmetic.Sub;
                case 2:
                    return BinaryArithmetic.Mul;
                case 3:
                    return BinaryArithmetic.TrueDiv;
                case 4:
                    return BinaryArithmetic.LShift;
                case 5:
                    return BinaryArithmetic.RShift;
                case 6:
                    return BinaryArithmetic.Or;
                case 7:
                    return BinaryArithmetic.And;
                case 8:
                    return BinaryArithmetic.Xor;
                case 9:
                    return BinaryArithmetic.FloorDiv;
                case 10:
                    return BinaryArithmetic.Mod;
                case 12:
                    return BinaryArithmetic.MatMul;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid binary operator");
            }
        }

    }

    // directly called without landing function
    @Builtin(name = "PyNumber_InPlaceBinOp", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyNumberInPlaceBinOp extends PythonTernaryBuiltinNode {
        static int MAX_CACHE_SIZE = InplaceArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()", "right.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        static Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, PrimitiveNativeWrapper right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(callNode.execute(frame, left.getLong(), right.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        static Object doObject(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached AsPythonObjectNode rightToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                if (left instanceof PrimitiveNativeWrapper || right instanceof PrimitiveNativeWrapper) {
                    Object leftValue;
                    Object rightValue;
                    if (left instanceof PrimitiveNativeWrapper) {
                        leftValue = PyNumberBinOp.extract((PrimitiveNativeWrapper) left);
                    } else {
                        leftValue = leftToJava.execute(left);
                    }
                    if (right instanceof PrimitiveNativeWrapper) {
                        rightValue = PyNumberBinOp.extract((PrimitiveNativeWrapper) right);
                    } else {
                        rightValue = rightToJava.execute(right);
                    }
                    result = callNode.execute(frame, leftValue, rightValue);
                } else {
                    result = callNode.execute(frame, leftToJava.execute(left), rightToJava.execute(right));
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getNativeNullNode.execute();
            }
            return toSulongNode.execute(result);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_binop}.
         */
        static LookupAndCallInplaceNode createCallNode(int op) {
            return getInplaceArithmetic(op).create();
        }

        private static InplaceArithmetic getInplaceArithmetic(int op) {
            switch (op) {
                case 0:
                    return InplaceArithmetic.IAdd;
                case 1:
                    return InplaceArithmetic.ISub;
                case 2:
                    return InplaceArithmetic.IMul;
                case 3:
                    return InplaceArithmetic.ITrueDiv;
                case 4:
                    return InplaceArithmetic.ILShift;
                case 5:
                    return InplaceArithmetic.IRShift;
                case 6:
                    return InplaceArithmetic.IOr;
                case 7:
                    return InplaceArithmetic.IAnd;
                case 8:
                    return InplaceArithmetic.IXor;
                case 9:
                    return InplaceArithmetic.IFloorDiv;
                case 10:
                    return InplaceArithmetic.IMod;
                case 12:
                    return InplaceArithmetic.IMatMul;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid binary operator");
            }
        }

    }

    // directly called without landing function
    @Builtin(name = "PyNumber_InPlacePower", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyNumberInPlacePower extends PythonTernaryBuiltinNode {
        @Child private LookupAndCallInplaceNode callNode;

        @Specialization(guards = {"o1.isIntLike()", "o2.isIntLike()", "o3.isIntLike()"})
        Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper o1, PrimitiveNativeWrapper o2, PrimitiveNativeWrapper o3,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(ensureCallNode().executeTernary(frame, o1.getLong(), o2.getLong(), o3.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(replaces = "doIntLikePrimitiveWrapper")
        Object doGeneric(VirtualFrame frame, Object o1, Object o2, Object o3,
                        @Cached AsPythonObjectNode o1ToJava,
                        @Cached AsPythonObjectNode o2ToJava,
                        @Cached AsPythonObjectNode o3ToJava,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                Object o1Value = unwrap(o1, o1ToJava);
                Object o2Value = unwrap(o2, o2ToJava);
                Object o3Value = unwrap(o3, o3ToJava);
                result = ensureCallNode().executeTernary(frame, o1Value, o2Value, o3Value);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getNativeNullNode.execute();
            }
            return toSulongNode.execute(result);
        }

        private static Object unwrap(Object left, AsPythonObjectNode toJavaNode) {
            if (left instanceof PrimitiveNativeWrapper) {
                return PyNumberBinOp.extract((PrimitiveNativeWrapper) left);
            } else {
                return toJavaNode.execute(left);
            }
        }

        private LookupAndCallInplaceNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(InplaceArithmetic.IPow.create());
            }
            return callNode;
        }
    }

    // directly called without landing function
    @Builtin(name = "AddMember", takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class AddMemberNode extends PythonVarargsBuiltinNode {

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return execute(frame, self, arguments, keywords);
        }

        @Specialization
        int doWithPrimitives(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (arguments.length != 7) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "AddMember", 7, arguments.length);
                }
                addMember(getLanguage(), arguments[0], arguments[1], arguments[2], castInt(arguments[3]), castInt(arguments[4]), castInt(arguments[5]), arguments[6],
                                AsPythonObjectNodeGen.getUncached(), CastToJavaStringNode.getUncached(), FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached(),
                                PythonObjectFactory.getUncached(), WriteAttributeToDynamicObjectNode.getUncached(), HashingStorageLibrary.getUncached());
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @TruffleBoundary
        private static void addMember(PythonLanguage language, Object clsPtr, Object tpDictPtr, Object namePtr, int memberType, int offset, int canSet, Object docPtr,
                        AsPythonObjectNode asPythonObjectNode,
                        CastToJavaStringNode castToJavaStringNode,
                        FromCharPointerNode fromCharPointerNode,
                        InteropLibrary docPtrLib,
                        PythonObjectFactory factory,
                        WriteAttributeToDynamicObjectNode writeDocNode,
                        HashingStorageLibrary dictStorageLib) {

            Object clazz = asPythonObjectNode.execute(clsPtr);
            PDict tpDict = castPDict(asPythonObjectNode.execute(tpDictPtr));
            String memberName;
            try {
                memberName = castToJavaStringNode.execute(asPythonObjectNode.execute(namePtr));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
            }
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, fromCharPointerNode, docPtrLib);
            PBuiltinFunction getterObject = ReadMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, offset);

            Object setterObject = null;
            if (canSet != 0) {
                setterObject = WriteMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, offset);
            }

            // create member descriptor
            GetSetDescriptor memberDescriptor = factory.createMemberDescriptor(getterObject, setterObject, memberName, clazz);
            writeDocNode.execute(memberDescriptor, SpecialAttributeNames.__DOC__, memberDoc);

            // add member descriptor to tp_dict
            HashingStorage dictStorage = tpDict.getDictStorage();
            HashingStorage updatedStorage = dictStorageLib.setItem(dictStorage, memberName, memberDescriptor);
            if (dictStorage != updatedStorage) {
                tpDict.setDictStorage(updatedStorage);
            }
        }
    }

    static PDict castPDict(Object tpDictObj) {
        if (tpDictObj instanceof PDict) {
            return (PDict) tpDictObj;
        }
        throw CompilerDirectives.shouldNotReachHere("tp_dict object must be a Python dict");
    }

    static int castInt(Object object) {
        if (object instanceof Integer) {
            return (int) object;
        } else if (object instanceof Long) {
            long lval = (long) object;
            if (PInt.isIntRange(lval)) {
                return (int) lval;
            }
        }
        throw CompilerDirectives.shouldNotReachHere("expected Java int");
    }

    abstract static class CreateGetSetNode extends Node {

        abstract GetSetDescriptor execute(String name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory);

        @Specialization
        static GetSetDescriptor createGetSet(String name, Object clazz, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CharPtrToJavaObjectNode fromCharPointerNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object cls = asPythonObjectNode.execute(clazz);
            PBuiltinFunction get = null;
            if (!interopLibrary.isNull(getter)) {
                RootCallTarget getterCT = getterCallTarget(name, language);
                get = factory.createGetSetBuiltinFunction(name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(getter, closure), getterCT);
            }

            PBuiltinFunction set = null;
            boolean hasSetter = !interopLibrary.isNull(setter);
            if (hasSetter) {
                RootCallTarget setterCT = setterCallTarget(name, language);
                set = factory.createGetSetBuiltinFunction(name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(setter, closure), setterCT);
            }

            // create get-set descriptor
            GetSetDescriptor descriptor = factory.createGetSetDescriptor(get, set, name, cls, hasSetter);
            Object memberDoc = fromCharPointerNode.execute(doc);
            dylib.put(descriptor.getStorage(), __DOC__, memberDoc);
            return descriptor;
        }

        @TruffleBoundary
        private static RootCallTarget getterCallTarget(String name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new GetterRoot(l, name, PExternalFunctionWrapper.GETTER);
            return lang.createCachedCallTarget(rootNodeFunction, GetterRoot.class, PExternalFunctionWrapper.GETTER, name, true);
        }

        @TruffleBoundary
        private static RootCallTarget setterCallTarget(String name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new SetterRoot(l, name, PExternalFunctionWrapper.SETTER);
            return lang.createCachedCallTarget(rootNodeFunction, SetterRoot.class, PExternalFunctionWrapper.SETTER, name, true);
        }
    }

    // directly called without landing function
    @Builtin(name = "PyDescr_NewGetSet", minNumOfPositionalArgs = 6, parameterNames = {"name", "cls", "getter", "setter", "doc", "closure"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class PyDescrNewGetSetNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.PyDescrNewGetSetNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doNativeCallable(String name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        @Cached CreateGetSetNode createGetSetNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
            GetSetDescriptor descr = createGetSetNode.execute(name, cls, getter, setter, doc, closure,
                            getLanguage(), factory());
            return toSulongNode.execute(descr);
        }
    }

    // directly called without landing function
    @Builtin(name = "AddGetSet", minNumOfPositionalArgs = 7, parameterNames = {"cls", "tpDict", "name", "getter", "setter", "doc", "closure"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class AddGetSetNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.AddGetSetNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int doGeneric(Object cls, Object tpDict, String name, Object getter, Object setter, Object doc, Object closure,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CreateGetSetNode createGetSetNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary dictStorageLib,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                GetSetDescriptor descr = createGetSetNode.execute(name, cls, getter, setter, doc, closure, getLanguage(), factory());
                PDict dict = PythonCextBuiltins.castPDict(asPythonObjectNode.execute(tpDict));
                HashingStorage dictStorage = dict.getDictStorage();
                HashingStorage updatedStorage = dictStorageLib.setItem(dictStorage, name, descr);
                if (dictStorage != updatedStorage) {
                    dict.setDictStorage(updatedStorage);
                }
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "_PyObject_Dump", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectDump extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        int doGeneric(Object ptrObject) {
            PythonContext context = getContext();
            PrintWriter stderr = new PrintWriter(context.getStandardErr());
            CApiContext cApiContext = context.getCApiContext();
            InteropLibrary lib = InteropLibrary.getUncached(ptrObject);
            PCallCapiFunction callNode = PCallCapiFunction.getUncached();

            // There are three cases we need to distinguish:
            // 1) The pointer object is a native pointer and is NOT a handle
            // 2) The pointer object is a native pointer and is a handle
            // 3) The pointer object is one of our native wrappers

            boolean isWrapper = CApiGuards.isNativeWrapper(ptrObject);
            boolean pointsToHandleSpace = !isWrapper && (boolean) callNode.call(NativeCAPISymbol.FUN_POINTS_TO_HANDLE_SPACE, ptrObject);
            boolean isValidHandle = pointsToHandleSpace && (boolean) callNode.call(NativeCAPISymbol.FUN_IS_HANDLE, ptrObject);

            /*
             * If the pointer points to the handle space but it's not a valid handle or if we do
             * memory tracing and we know that the pointer is not allocated (was free'd), we assumed
             * it's a use-after-free.
             */
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            if (pointsToHandleSpace && !isValidHandle || traceNativeMemory && !isWrapper && !cApiContext.isAllocated(ptrObject)) {
                stderr.println(String.format("<object at %s is freed>", CApiContext.asPointer(ptrObject, lib)));
                stderr.flush();
                return 0;
            }

            /*
             * At this point we don't know if the pointer is invalid, so we try to resolve it to an
             * object.
             */
            Object resolved = isWrapper ? ptrObject : ResolveHandleNodeGen.getUncached().execute(ptrObject);
            Object pythonObject;
            long refCnt;
            // We need again check if 'resolved' is a wrapper in case we resolved a handle.
            if (CApiGuards.isNativeWrapper(resolved)) {
                PythonNativeWrapper wrapper = (PythonNativeWrapper) resolved;
                refCnt = wrapper.getRefCount();
                pythonObject = AsPythonObjectNodeGen.getUncached().execute(wrapper);
            } else {
                long obRefCnt = GetRefCntNodeGen.getUncached().execute(cApiContext, ptrObject);
                /*
                 * The upper 32-bits of the native field 'ob_refcnt' encode an ID into the native
                 * object reference table. We mask them out to get the real reference count.
                 */
                refCnt = obRefCnt & (CApiContext.REFERENCE_COUNT_MARKER - 1);
                pythonObject = AsPythonObjectNodeGen.getUncached().execute(ResolveNativeReferenceNodeGen.getUncached().execute(resolved, obRefCnt, false));
            }

            // first, write fields which are the least likely to crash
            stderr.println("ptrObject address  : " + ptrObject);
            stderr.println("ptrObject refcount : " + refCnt);
            stderr.flush();

            Object type = GetClassNode.getUncached().execute(pythonObject);
            stderr.println("object type     : " + type);
            stderr.println("object type name: " + GetNameNode.getUncached().execute(type));

            // the most dangerous part
            stderr.println("object repr     : ");
            stderr.flush();
            try {
                Object reprObj = PyObjectCallMethodObjArgs.getUncached().execute(null, context.getBuiltins(), BuiltinNames.REPR, pythonObject);
                stderr.println(CastToJavaStringNode.getUncached().execute(reprObj));
            } catch (PException | CannotCastException e) {
                // errors are ignored at this point
            }
            stderr.flush();
            return 0;
        }
    }

    // directly called without landing function
    @Builtin(name = "Py_AtExit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyAtExit extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        int doGeneric(Object funcPtr) {
            getContext().registerAtexitHook(new ShutdownHook() {
                @Override
                public void call(@SuppressWarnings("unused") PythonContext context) {
                    try {
                        InteropLibrary.getUncached().execute(funcPtr);
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        // ignored
                    }
                }
            });
            return 0;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyStructSequence_InitType2", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyStructSequenceInitType2 extends NativeBuiltin {

        @Specialization(limit = "1")
        static int doGeneric(Object klass, Object fieldNamesObj, Object fieldDocsObj, int nInSequence,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary("fieldNamesObj") InteropLibrary lib,
                        @Cached(parameters = "true") WriteAttributeToObjectNode clearNewNode) {
            return initializeStructType(asPythonObjectNode.execute(klass), fieldNamesObj, fieldDocsObj, nInSequence, PythonLanguage.get(lib), lib, clearNewNode);
        }

        static int initializeStructType(Object klass, Object fieldNamesObj, Object fieldDocsObj, int nInSequence,
                        PythonLanguage language,
                        InteropLibrary lib,
                        WriteAttributeToObjectNode clearNewNode) {
            // 'fieldNames' and 'fieldDocs' must be of same type; they share the interop lib
            assert fieldNamesObj.getClass() == fieldDocsObj.getClass();

            try {
                int n = PInt.intValueExact(lib.getArraySize(fieldNamesObj));
                if (n != lib.getArraySize(fieldDocsObj)) {
                    // internal error: the C function must type the object correctly
                    throw CompilerDirectives.shouldNotReachHere("len(fieldNames) != len(fieldDocs)");
                }
                String[] fieldNames = new String[n];
                String[] fieldDocs = new String[n];
                for (int i = 0; i < n; i++) {
                    fieldNames[i] = cast(lib.readArrayElement(fieldNamesObj, i));
                    fieldDocs[i] = cast(lib.readArrayElement(fieldDocsObj, i));
                }
                clearNewNode.execute(klass, __NEW__, PNone.NO_VALUE);
                Descriptor d = new Descriptor(null, nInSequence, fieldNames, fieldDocs);
                StructSequence.initType(language, klass, d);
                return 0;
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (OverflowException e) {
                // fall through
            }
            return -1;
        }

        private static String cast(Object object) {
            if (object instanceof String) {
                return (String) object;
            }
            throw CompilerDirectives.shouldNotReachHere("object is expected to be a Java string");
        }
    }

    // directly called without landing function
    @Builtin(name = "PyStructSequence_NewType", minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyStructSequenceNewType extends NativeBuiltin {

        @Specialization(limit = "1")
        Object doGeneric(VirtualFrame frame, String typeName, String typeDoc, Object fieldNamesObj, Object fieldDocsObj, int nInSequence,
                        @Cached ReadAttributeFromObjectNode readTypeBuiltinNode,
                        @Cached CallNode callTypeNewNode,
                        @CachedLibrary("fieldNamesObj") InteropLibrary lib,
                        @Cached(parameters = "true") WriteAttributeToObjectNode clearNewNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached ToNewRefNode toNewRefNode) {
            try {
                Object typeBuiltin = readTypeBuiltinNode.execute(getCore().getBuiltins(), BuiltinNames.TYPE);
                PTuple bases = factory().createTuple(new Object[]{PythonBuiltinClassType.PTuple});
                PDict namespace = factory().createDict(new PKeyword[]{new PKeyword(SpecialAttributeNames.__DOC__, typeDoc)});
                Object cls = callTypeNewNode.execute(typeBuiltin, typeName, bases, namespace);
                PyStructSequenceInitType2.initializeStructType(cls, fieldNamesObj, fieldDocsObj, nInSequence, getLanguage(), lib, clearNewNode);
                return toNewRefNode.execute(cls);
            } catch (PException e) {
                transformToNative(frame, e);
                return getNativeNullNode.execute();
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyStructSequence_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyStructSequenceNew extends PythonUnaryBuiltinNode {

        @Specialization
        Object doGeneric(Object clsPtr,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readRealSizeNode,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached ToNewRefNode toNewRefNode) {
            try {
                Object cls = asPythonObjectNode.execute(clsPtr);
                Object realSizeObj = readRealSizeNode.execute(cls, StructSequence.N_FIELDS);
                Object res;
                if (realSizeObj == PNone.NO_VALUE) {
                    PRaiseNativeNode.raiseNative(null, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC, EMPTY_OBJECT_ARRAY, getRaiseNode(), transformExceptionToNativeNode);
                    res = getNativeNullNode.execute();
                } else {
                    int realSize = castToIntNode.execute(realSizeObj);
                    res = factory().createTuple(cls, new Object[realSize]);
                }
                return toNewRefNode.execute(res);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("attribute 'n_fields' is expected to be a Java int");
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyState_FindModule", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyStateFindModule extends PythonUnaryBuiltinNode {

        @Specialization
        Object doGeneric(long mIndex,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object result;
            try {
                int i = PInt.intValueExact(mIndex);
                result = getContext().getCApiContext().getModuleByIndex(i);
                if (result == null) {
                    result = getNativeNullNode.execute();
                }
            } catch (CannotCastException | OverflowException e) {
                result = getNativeNullNode.execute();
            }
            return toSulongNode.execute(result);
        }
    }

    @Builtin(name = "PyType_Lookup", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTypeLookup extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object type, Object name,
                        @Cached AsPythonObjectNode typeAsPythonObjectNode,
                        @Cached AsPythonObjectNode nameAsPythonObjectNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttributeInMRONode,
                        @Cached ToBorrowedRefNode toBorrowedRefNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            Object result = lookupAttributeInMRONode.execute(typeAsPythonObjectNode.execute(type), nameAsPythonObjectNode.execute(name));
            if (result == PNone.NO_VALUE) {
                return getNativeNullNode.execute();
            }
            return toBorrowedRefNode.execute(result);
        }
    }

    // directly called without landing function
    @Builtin(name = "PyEval_EvalCodeEx", minNumOfPositionalArgs = 9, needsFrame = true)
    @GenerateNodeFactory
    abstract static class PyEvalEvalCodeEx extends PythonBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object codeWrapper, Object globalsWrapper, Object localsWrapper,
                        Object argumentArrayPtr, Object kwnamesPtr, Object keywordArrayPtr, Object defaultValueArrayPtr,
                        Object kwdefaultsWrapper, Object closureWrapper,
                        @CachedLibrary(limit = "2") InteropLibrary ptrLib,
                        @Cached AsPythonObjectNode codeAsPythonObjectNode,
                        @Cached AsPythonObjectNode globalsAsPythonObjectNode,
                        @Cached AsPythonObjectNode localsAsPythonObjectNode,
                        @Cached AsPythonObjectNode kwdefaultsAsPythonObjectNode,
                        @Cached AsPythonObjectNode closureAsPythonObjectNode,
                        @Cached ToJavaNode elementToJavaNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached GetObjectArrayNode getObjectArrayNode,
                        @Cached GetCodeSignatureNode getSignatureNode,
                        @Cached GetCodeCallTargetNode getCallTargetNode,
                        @Cached CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                        @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached GenericInvokeNode invokeNode) {
            PCode code = (PCode) codeAsPythonObjectNode.execute(codeWrapper);
            Object globals = globalsAsPythonObjectNode.execute(globalsWrapper);
            Object locals = localsAsPythonObjectNode.execute(localsWrapper);
            Object[] defaults = unwrapArray(defaultValueArrayPtr, ptrLib, elementToJavaNode);
            PKeyword[] kwdefaults = expandKeywordStarargsNode.execute(kwdefaultsAsPythonObjectNode.execute(kwdefaultsWrapper));
            PCell[] closure = null;
            Object closureObj = closureAsPythonObjectNode.execute(closureWrapper);
            if (closureObj != PNone.NO_VALUE) {
                // CPython also just accesses the object as tuple without further checks.
                closure = PCell.toCellArray(getObjectArrayNode.execute(closureObj));
            }
            Object[] keywordNames = unwrapArray(kwnamesPtr, ptrLib, elementToJavaNode);
            Object[] keywordArguments = unwrapArray(keywordArrayPtr, ptrLib, elementToJavaNode);

            // The two arrays 'kwnamesPtr' and 'keywordArrayPtr' are expected to have the same size.
            if (keywordNames.length != keywordArguments.length) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            PKeyword[] keywords = new PKeyword[keywordNames.length];
            for (int i = 0; i < keywordNames.length; i++) {
                String keywordName = castToJavaStringNode.execute(keywordNames[i]);
                keywords[i] = new PKeyword(keywordName, keywordArguments[i]);
            }

            // prepare Python frame arguments
            Object[] userArguments = unwrapArray(argumentArrayPtr, ptrLib, elementToJavaNode);
            Signature signature = getSignatureNode.execute(code);
            Object[] pArguments = createAndCheckArgumentsNode.execute(code, userArguments, keywords, signature, null, defaults, kwdefaults, false);

            // set custom locals
            if (!(locals instanceof PNone)) {
                PArguments.setSpecialArgument(pArguments, locals);
                PArguments.setCustomLocals(pArguments, locals);
            }
            PArguments.setClosure(pArguments, closure);
            // TODO(fa): set builtins in globals
            // PythonModule builtins = getContext().getBuiltins();
            // setBuiltinsInGlobals(frame, globals, setBuiltins, builtins, lib);
            if (globals instanceof PythonObject) {
                PArguments.setGlobals(pArguments, (PythonObject) globals);
            } else {
                // TODO(fa): raise appropriate exception
            }

            try {
                RootCallTarget rootCallTarget = getCallTargetNode.execute(code);
                Object result = invokeNode.execute(frame, rootCallTarget, pArguments);
                return toNewRefNode.execute(result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getNativeNullNode.execute();
            }
        }

        private static Object[] unwrapArray(Object ptr, InteropLibrary ptrLib, ToJavaNode elementToJavaNode) {
            if (ptrLib.hasArrayElements(ptr)) {
                try {
                    int size = PInt.intValueExact(ptrLib.getArraySize(ptr));
                    Object[] result = new Object[size];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = elementToJavaNode.execute(ptrLib.readArrayElement(ptr, i));
                    }
                    return result;
                } catch (UnsupportedMessageException | OverflowException | InvalidArrayIndexException e) {
                    // fall through
                }
            }
            /*
             * Whenever some access goes wrong then this would basically be a segfault in CPython.
             * So, we just throw a fatal exception which is not a Python exception.
             */
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = "PySet_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PySetSize extends PythonUnaryBuiltinNode {
        @Specialization
        static int doSet(PSet type,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            return lib.length(type.getDictStorage());
        }
    }

    @Builtin(name = "PyUnicode_ReadChar", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyUnicodeReadChar extends PythonBinaryBuiltinNode {
        @Specialization
        int doGeneric(Object type, long lindex,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                try {
                    String s = castToJavaStringNode.execute(type);
                    int index = PInt.intValueExact(lindex);
                    // avoid StringIndexOutOfBoundsException
                    if (index < 0 || index >= PString.length(s)) {
                        throw raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                    }
                    return PString.charAt(s, index);
                } catch (CannotCastException e) {
                    throw raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
                } catch (OverflowException e) {
                    throw raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyTruffle_tss_create")
    @GenerateNodeFactory
    abstract static class PyTruffleTssCreate extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        long tssCreate() {
            return getContext().getCApiContext().nextTssKey();
        }
    }

    @Builtin(name = "PyTruffle_tss_get")
    @GenerateNodeFactory
    abstract static class PyTruffleTssGet extends PythonUnaryBuiltinNode {
        @Specialization
        Object tssGet(Object key,
                        @Cached CastToJavaLongLossyNode cast,
                        @Cached GetNativeNullNode getNativeNullNode) {
            Object value = getContext().getCApiContext().tssGet(cast.execute(key));
            if (value == null) {
                return getNativeNullNode.execute();
            }
            return value;
        }
    }

    @Builtin(name = "PyTruffle_tss_set")
    @GenerateNodeFactory
    abstract static class PyTruffleTssSet extends PythonBinaryBuiltinNode {
        @Specialization
        Object tssSet(Object key, Object value,
                        @Cached CastToJavaLongLossyNode cast) {
            getContext().getCApiContext().tssSet(cast.execute(key), value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_tss_delete")
    @GenerateNodeFactory
    abstract static class PyTruffleTssDelete extends PythonUnaryBuiltinNode {
        @Specialization
        Object tssDelete(Object key,
                        @Cached CastToJavaLongLossyNode cast) {
            getContext().getCApiContext().tssDelete(cast.execute(key));
            return PNone.NONE;
        }
    }
}
