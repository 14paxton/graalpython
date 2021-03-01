/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.runtime.object;

import java.io.ByteArrayOutputStream;
import java.lang.ref.ReferenceQueue;
import java.math.BigInteger;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.Semaphore;

import org.graalvm.collections.EconomicMap;
import org.tukaani.xz.FinishableOutputStream;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixFileHandle;
import com.oracle.graal.python.builtins.modules.bz2.BZ2Object;
import com.oracle.graal.python.builtins.modules.io.PBuffered;
import com.oracle.graal.python.builtins.modules.io.PFileIO;
import com.oracle.graal.python.builtins.modules.zlib.ZLibCompObject;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemIterator;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeyIterator;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValueIterator;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenPythonKey;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBigRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.lzma.PLZMACompressor;
import com.oracle.graal.python.builtins.objects.lzma.PLZMADecompressor;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.ManagedBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.posix.PDirEntry;
import com.oracle.graal.python.builtins.objects.posix.PScandirIterator;
import com.oracle.graal.python.builtins.objects.random.PRandom;
import com.oracle.graal.python.builtins.objects.range.PBigRange;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator;
import com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PIntSlice;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PSemLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence.Descriptor;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

@GenerateUncached
@ImportStatic(PythonOptions.class)
public abstract class PythonObjectFactory extends Node {

    public static PythonObjectFactory create() {
        return PythonObjectFactoryNodeGen.create();
    }

    public static PythonObjectFactory getUncached() {
        return PythonObjectFactoryNodeGen.getUncached();
    }

    protected abstract AllocationReporter executeTrace(Object o, long size);

    protected abstract Shape executeGetShape(Object o, boolean flag);

    protected abstract PythonLanguage executeGetLanguage(boolean marker, double marker2);

    @Specialization
    static final Shape getShape(Object o, @SuppressWarnings("unused") boolean flag,
                    @Cached TypeNodes.GetInstanceShape getShapeNode) {
        return getShapeNode.execute(o);
    }

    @Specialization
    static final AllocationReporter doTrace(Object o, long size,
                    @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") ContextReference<PythonContext> contextRef,
                    @Cached(value = "getAllocationReporter(contextRef)", allowUncached = true) AllocationReporter reporter) {
        if (reporter.isActive()) {
            reporter.onEnter(null, 0, size);
            reporter.onReturnValue(o, 0, size);
        }
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization
    static final PythonLanguage getLanguage(boolean marker, double marker2,
                    @CachedLanguage PythonLanguage lang) {
        return lang;
    }

    protected static AllocationReporter getAllocationReporter(ContextReference<PythonContext> contextRef) {
        return contextRef.get().getEnv().lookup(AllocationReporter.class);
    }

    public PythonLanguage getLanguage() {
        return executeGetLanguage(true, 0.0);
    }

    public final Shape getShape(Object cls) {
        return executeGetShape(cls, true);
    }

    public final <T> T trace(T allocatedObject) {
        executeTrace(allocatedObject, AllocationReporter.SIZE_UNKNOWN);
        return allocatedObject;
    }

    /*
     * Python objects
     */

    /**
     * Creates a PythonObject for the given class. This is potentially slightly slower than if the
     * shape had been cached, due to the additional shape lookup.
     */
    public PythonObject createPythonObject(Object cls) {
        return createPythonObject(cls, getShape(cls));
    }

    /**
     * Creates a Python object with the given shape. Python object shapes store the class in the
     * shape if possible.
     */
    public PythonObject createPythonObject(Object klass, Shape instanceShape) {
        return trace(new PythonObject(klass, instanceShape));
    }

    public PythonNativeVoidPtr createNativeVoidPtr(TruffleObject obj) {
        return trace(new PythonNativeVoidPtr(obj));
    }

    public PythonNativeVoidPtr createNativeVoidPtr(TruffleObject obj, long nativePtr) {
        return trace(new PythonNativeVoidPtr(obj, nativePtr));
    }

    public SuperObject createSuperObject(Object self) {
        return trace(new SuperObject(self, getShape(self)));
    }

    /*
     * Primitive types
     */
    public PInt createInt(int value) {
        return createInt(PInt.longToBigInteger(value));
    }

    public PInt createInt(long value) {
        return createInt(PInt.longToBigInteger(value));
    }

    public PInt createInt(BigInteger value) {
        return createInt(PythonBuiltinClassType.PInt, value);
    }

    public Object createInt(Object cls, int value) {
        return createInt(cls, PInt.longToBigInteger(value));
    }

    public Object createInt(Object cls, long value) {
        return createInt(cls, PInt.longToBigInteger(value));
    }

    public PInt createInt(Object cls, BigInteger value) {
        return trace(new PInt(cls, getShape(cls), value));
    }

    public PFloat createFloat(double value) {
        return createFloat(PythonBuiltinClassType.PFloat, value);
    }

    public PFloat createFloat(Object cls, double value) {
        return trace(new PFloat(cls, getShape(cls), value));
    }

    public PString createString(String string) {
        return createString(PythonBuiltinClassType.PString, string);
    }

    public PString createString(Object cls, String string) {
        return trace(new PString(cls, getShape(cls), string));
    }

    public PString createString(CharSequence string) {
        return createString(PythonBuiltinClassType.PString, string);
    }

    public PString createString(Object cls, CharSequence string) {
        return trace(new PString(cls, getShape(cls), string));
    }

    public PBytes createBytes(byte[] array) {
        return createBytes(array, array.length);
    }

    public PBytes createBytes(byte[] array, int offset, int length) {
        if (length != array.length) {
            byte[] buf = new byte[length];
            PythonUtils.arraycopy(array, offset, buf, 0, buf.length);
            return createBytes(PythonBuiltinClassType.PBytes, buf);
        }
        return createBytes(PythonBuiltinClassType.PBytes, array);
    }

    public PBytes createBytes(Object cls, byte[] array) {
        return createBytes(cls, array, array.length);
    }

    public PBytes createBytes(byte[] array, int length) {
        return createBytes(new ByteSequenceStorage(array, length));
    }

    public PBytes createBytes(Object cls, byte[] array, int length) {
        return createBytes(cls, new ByteSequenceStorage(array, length));
    }

    public PBytes createBytes(SequenceStorage storage) {
        return createBytes(PythonBuiltinClassType.PBytes, storage);
    }

    public PBytes createBytes(Object cls, SequenceStorage storage) {
        return trace(new PBytes(cls, getShape(cls), storage));
    }

    public final PTuple createEmptyTuple() {
        return createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PTuple createEmptyTuple(Object cls) {
        return createTuple(cls, EmptySequenceStorage.INSTANCE);
    }

    public final PTuple createTuple(Object[] objects) {
        return createTuple(PythonBuiltinClassType.PTuple, objects);
    }

    public final PTuple createTuple(int[] ints) {
        return createTuple(new IntSequenceStorage(ints));
    }

    public final PTuple createTuple(SequenceStorage store) {
        return createTuple(PythonBuiltinClassType.PTuple, store);
    }

    public final PTuple createTuple(Object cls, Object[] objects) {
        return trace(new PTuple(cls, getShape(cls), objects));
    }

    public final PTuple createTuple(Object cls, SequenceStorage store) {
        return trace(new PTuple(cls, getShape(cls), store));
    }

    public final PTuple createStructSeq(Descriptor desc, Object... values) {
        assert desc.inSequence <= values.length && values.length <= desc.fieldNames.length;
        return createTuple(desc.type, new ObjectSequenceStorage(values, desc.inSequence));
    }

    public final PComplex createComplex(Object cls, double real, double imag) {
        return trace(new PComplex(cls, getShape(cls), real, imag));
    }

    public final PComplex createComplex(double real, double imag) {
        return createComplex(PythonBuiltinClassType.PComplex, real, imag);
    }

    public PIntRange createIntRange(int stop) {
        return trace(new PIntRange(getLanguage(), 0, stop, 1, stop));
    }

    public PIntRange createIntRange(int start, int stop, int step, int len) {
        return trace(new PIntRange(getLanguage(), start, stop, step, len));
    }

    public PBigRange createBigRange(BigInteger start, BigInteger stop, BigInteger step, BigInteger len) {
        return createBigRange(createInt(start), createInt(stop), createInt(step), createInt(len));
    }

    public PBigRange createBigRange(PInt start, PInt stop, PInt step, PInt len) {
        return trace(new PBigRange(getLanguage(), start, stop, step, len));
    }

    public PIntSlice createIntSlice(int start, int stop, int step) {
        return trace(new PIntSlice(getLanguage(), start, stop, step));
    }

    public PIntSlice createIntSlice(int start, int stop, int step, boolean isStartNone, boolean isStepNone) {
        return trace(new PIntSlice(getLanguage(), start, stop, step, isStartNone, isStepNone));
    }

    public PObjectSlice createObjectSlice(Object start, Object stop, Object step) {
        return trace(new PObjectSlice(getLanguage(), start, stop, step));
    }

    public PRandom createRandom(Object cls) {
        return trace(new PRandom(cls, getShape(cls)));
    }

    /*
     * Classes, methods and functions
     */

    public PythonModule createPythonModule(String name) {
        return trace(PythonModule.createInternal(name));
    }

    public PythonModule createPythonModule(Object cls) {
        return trace(new PythonModule(cls, getShape(cls)));
    }

    public PythonClass createPythonClass(Object metaclass, String name, PythonAbstractClass[] bases) {
        return trace(new PythonClass(getLanguage(), metaclass, getShape(metaclass), name, bases));
    }

    public PythonClass createPythonClass(Object metaclass, String name, boolean invokeMro, PythonAbstractClass[] bases) {
        return trace(new PythonClass(getLanguage(), metaclass, getShape(metaclass), name, invokeMro, bases));
    }

    public PMemoryView createMemoryView(PythonContext context, ManagedBuffer managedBuffer, Object owner,
                    int len, boolean readonly, int itemsize, BufferFormat format, String formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.PMemoryView;
        return trace(new PMemoryView(cls, getShape(cls), context, managedBuffer, owner, len, readonly, itemsize, format, formatString,
                        ndim, bufPointer, offset, shape, strides, suboffsets, flags));
    }

    public PMemoryView createMemoryView(PythonContext context, ManagedBuffer managedBuffer, Object owner,
                    int len, boolean readonly, int itemsize, String formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.PMemoryView;
        return trace(new PMemoryView(cls, getShape(cls), context, managedBuffer, owner, len, readonly, itemsize,
                        BufferFormat.forMemoryView(formatString), formatString, ndim, bufPointer, offset, shape, strides, suboffsets, flags));
    }

    public PMemoryView createMemoryViewForManagedObject(Object object, int itemsize, int length, boolean readonly, String format) {
        return createMemoryView(null, null, object, length * itemsize, readonly, itemsize, format, 1,
                        null, 0, new int[]{length}, new int[]{itemsize}, null,
                        PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN);
    }

    public final PMethod createMethod(Object cls, Object self, Object function) {
        return trace(new PMethod(cls, getShape(cls), self, function));
    }

    public final PMethod createMethod(Object self, Object function) {
        return createMethod(PythonBuiltinClassType.PMethod, self, function);
    }

    public final PMethod createBuiltinMethod(Object self, PFunction function) {
        return createMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public final PBuiltinMethod createBuiltinMethod(Object cls, Object self, PBuiltinFunction function) {
        return trace(new PBuiltinMethod(cls, getShape(cls), self, function));
    }

    public final PBuiltinMethod createBuiltinMethod(Object self, PBuiltinFunction function) {
        return createBuiltinMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(getLanguage(), name, name, enclosingClassName, code, globals, closure));
    }

    public PFunction createFunction(String name, String qualname, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure) {
        return trace(new PFunction(getLanguage(), name, qualname, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure));
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure) {
        return trace(new PFunction(getLanguage(), name, name, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure));
    }

    public PFunction createFunction(String name, String qualname, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure, Assumption codeStableAssumption, Assumption defaultsStableAssumption) {
        return trace(new PFunction(getLanguage(), name, qualname, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure,
                        codeStableAssumption, defaultsStableAssumption));
    }

    public PBuiltinFunction createBuiltinFunction(String name, Object type, int numDefaults, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(getLanguage(), name, type, numDefaults, callTarget));
    }

    public PBuiltinFunction createBuiltinFunction(String name, Object type, Object[] defaults, PKeyword[] kw, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(getLanguage(), name, type, defaults, kw, callTarget));
    }

    public GetSetDescriptor createGetSetDescriptor(Object get, Object set, String name, Object type) {
        return trace(new GetSetDescriptor(getLanguage(), get, set, name, type));
    }

    public GetSetDescriptor createGetSetDescriptor(Object get, Object set, String name, Object type, boolean allowsDelete) {
        return trace(new GetSetDescriptor(getLanguage(), get, set, name, type, allowsDelete));
    }

    public HiddenKeyDescriptor createHiddenKeyDescriptor(HiddenPythonKey key, Object type) {
        return trace(new HiddenKeyDescriptor(getLanguage(), key, type));
    }

    public PDecoratedMethod createClassmethod(Object cls) {
        return trace(new PDecoratedMethod(cls, getShape(cls)));
    }

    public PDecoratedMethod createClassmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PClassmethod, PythonBuiltinClassType.PClassmethod.getInstanceShape(getLanguage()), callable));
    }

    public PDecoratedMethod createBuiltinClassmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PBuiltinClassMethod, PythonBuiltinClassType.PBuiltinClassMethod.getInstanceShape(getLanguage()), callable));
    }

    public PDecoratedMethod createStaticmethod(Object cls) {
        return trace(new PDecoratedMethod(cls, getShape(cls)));
    }

    public PDecoratedMethod createStaticmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PStaticmethod, PythonBuiltinClassType.PStaticmethod.getInstanceShape(getLanguage()), callable));
    }

    /*
     * Lists, sets and dicts
     */

    public PList createList() {
        return createList(PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public PList createList(SequenceStorage storage) {
        return createList(PythonBuiltinClassType.PList, storage);
    }

    public PList createList(SequenceStorage storage, ListLiteralNode origin) {
        return trace(new PList(PythonBuiltinClassType.PList, PythonBuiltinClassType.PList.getInstanceShape(getLanguage()), storage, origin));
    }

    public PList createList(Object cls, SequenceStorage storage) {
        return trace(new PList(cls, getShape(cls), storage));
    }

    public PList createList(Object cls) {
        return createList(cls, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public PList createList(Object[] array) {
        return createList(PythonBuiltinClassType.PList, array);
    }

    public PList createList(Object cls, Object[] array) {
        return trace(new PList(cls, getShape(cls), SequenceStorageFactory.createStorage(array)));
    }

    public PSet createSet(Object cls) {
        return trace(new PSet(cls, getShape(cls)));
    }

    public PSet createSet(HashingStorage storage) {
        return trace(new PSet(PythonBuiltinClassType.PSet, PythonBuiltinClassType.PSet.getInstanceShape(getLanguage()), storage));
    }

    public PFrozenSet createFrozenSet(Object cls) {
        return trace(new PFrozenSet(cls, getShape(cls)));
    }

    public PFrozenSet createFrozenSet(Object cls, HashingStorage storage) {
        return trace(new PFrozenSet(cls, getShape(cls), storage));
    }

    public PFrozenSet createFrozenSet(HashingStorage storage) {
        return createFrozenSet(PythonBuiltinClassType.PFrozenSet, storage);
    }

    public PDict createDict() {
        return createDict(PythonBuiltinClassType.PDict);
    }

    public PDict createDict(PKeyword[] keywords) {
        return trace(new PDict(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.getInstanceShape(getLanguage()), keywords));
    }

    public PDict createDict(Object cls) {
        return trace(new PDict(cls, getShape(cls)));
    }

    public PDict createDict(EconomicMap<? extends Object, Object> map) {
        return createDict(EconomicMapStorage.create(map));
    }

    public PDict createDictLocals(MaterializedFrame frame) {
        return createDict(new LocalsStorage(frame));
    }

    public PDict createDictLocals(FrameDescriptor fd) {
        return createDict(new LocalsStorage(fd));
    }

    public PDict createDict(DynamicObject dynamicObject) {
        return createDict(new DynamicObjectStorage(dynamicObject));
    }

    public PDict createDictFixedStorage(PythonObject pythonObject, MroSequenceStorage mroSequenceStorage) {
        return createDict(new DynamicObjectStorage(pythonObject.getStorage(), mroSequenceStorage));
    }

    public PDict createDictFixedStorage(PythonObject pythonObject) {
        return createDict(new DynamicObjectStorage(pythonObject.getStorage()));
    }

    public PDict createDict(Object cls, HashingStorage storage) {
        return trace(new PDict(cls, getShape(cls), storage));
    }

    public PDict createDict(HashingStorage storage) {
        return createDict(PythonBuiltinClassType.PDict, storage);
    }

    public PDictView createDictKeysView(PHashingCollection dict) {
        return trace(new PDictKeysView(PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictKeysView.getInstanceShape(getLanguage()), dict));
    }

    public PDictView createDictValuesView(PHashingCollection dict) {
        return trace(new PDictValuesView(PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDictValuesView.getInstanceShape(getLanguage()), dict));
    }

    public PDictView createDictItemsView(PHashingCollection dict) {
        return trace(new PDictItemsView(PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictItemsView.getInstanceShape(getLanguage()), dict));
    }

    /*
     * Special objects: generators, proxies, references, cells
     */

    public PGenerator createGenerator(String name, String qualname, RootCallTarget[] callTargets, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure, ExecutionCellSlots cellSlots,
                    GeneratorInfo generatorInfo, Object iterator) {
        return trace(PGenerator.create(getLanguage(), name, qualname, callTargets, frameDescriptor, arguments, closure, cellSlots, generatorInfo, this, iterator));
    }

    public PMappingproxy createMappingproxy(PythonObject object) {
        PythonBuiltinClassType mpClass = PythonBuiltinClassType.PMappingproxy;
        return trace(new PMappingproxy(mpClass, mpClass.getInstanceShape(getLanguage()), object));
    }

    public PMappingproxy createMappingproxy(Object cls, PythonObject object) {
        return trace(new PMappingproxy(cls, getShape(cls), object));
    }

    public PReferenceType createReferenceType(Object cls, Object object, Object callback, ReferenceQueue<Object> queue) {
        return trace(new PReferenceType(cls, getShape(cls), object, callback, queue));
    }

    public PReferenceType createReferenceType(Object object, Object callback, ReferenceQueue<Object> queue) {
        return createReferenceType(PythonBuiltinClassType.PReferenceType, object, callback, queue);
    }

    public PCell createCell(Assumption effectivelyFinal) {
        return trace(new PCell(effectivelyFinal));
    }

    /*
     * Frames, traces and exceptions
     */

    public PFrame createPFrame(PFrame.Reference frameInfo, Node location, boolean inClassBody) {
        return trace(new PFrame(getLanguage(), frameInfo, location, inClassBody));
    }

    public PFrame createPFrame(PFrame.Reference frameInfo, Node location, Object locals, boolean inClassBody) {
        return trace(new PFrame(getLanguage(), frameInfo, location, locals, inClassBody));
    }

    public PFrame createPFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
        return trace(new PFrame(getLanguage(), threadState, code, globals, locals));
    }

    public PTraceback createTraceback(PFrame frame, int lineno, PTraceback next) {
        return trace(new PTraceback(getLanguage(), frame, lineno, next));
    }

    public PTraceback createTraceback(PFrame frame, int lineno, int lasti, PTraceback next) {
        return trace(new PTraceback(getLanguage(), frame, lineno, lasti, next));
    }

    public PTraceback createTraceback(LazyTraceback tb) {
        return trace(new PTraceback(getLanguage(), tb));
    }

    public PBaseException createBaseException(Object cls, PTuple args) {
        return trace(new PBaseException(cls, getShape(cls), args));
    }

    public PBaseException createBaseException(Object cls, String format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(cls, getShape(cls), format, args));
    }

    public PBaseException createBaseException(Object cls) {
        return trace(new PBaseException(cls, getShape(cls)));
    }

    /*
     * Arrays
     */

    public PArray createArray(Object cls, String formatString, BufferFormat format) {
        assert format != null;
        return trace(new PArray(cls, getShape(cls), formatString, format));
    }

    public PArray createArray(String formatString, BufferFormat format, int length) throws OverflowException {
        return createArray(PythonBuiltinClassType.PArray, formatString, format, length);
    }

    public PArray createArray(Object cls, String formatString, BufferFormat format, int length) throws OverflowException {
        assert format != null;
        return trace(new PArray(cls, getShape(cls), formatString, format, length));
    }

    public PByteArray createByteArray(byte[] array) {
        return createByteArray(array, array.length);
    }

    public PByteArray createByteArray(Object cls, byte[] array) {
        return createByteArray(cls, array, array.length);
    }

    public PByteArray createByteArray(byte[] array, int length) {
        return createByteArray(new ByteSequenceStorage(array, length));
    }

    public PByteArray createByteArray(Object cls, byte[] array, int length) {
        return createByteArray(cls, new ByteSequenceStorage(array, length));
    }

    public PByteArray createByteArray(SequenceStorage storage) {
        return createByteArray(PythonBuiltinClassType.PByteArray, storage);
    }

    public PByteArray createByteArray(Object cls, SequenceStorage storage) {
        return trace(new PByteArray(cls, getShape(cls), storage));
    }

    /*
     * Iterators
     */

    public PStringIterator createStringIterator(String str) {
        return trace(new PStringIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), str));
    }

    public PStringReverseIterator createStringReverseIterator(Object cls, String str) {
        return trace(new PStringReverseIterator(cls, getShape(cls), str));
    }

    public PIntegerSequenceIterator createIntegerSequenceIterator(IntSequenceStorage storage, PList list) {
        return trace(new PIntegerSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public PLongSequenceIterator createLongSequenceIterator(LongSequenceStorage storage, PList list) {
        return trace(new PLongSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public PDoubleSequenceIterator createDoubleSequenceIterator(DoubleSequenceStorage storage, PList list) {
        return trace(new PDoubleSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public PSequenceIterator createSequenceIterator(Object sequence) {
        return trace(new PSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), sequence));
    }

    public PSequenceReverseIterator createSequenceReverseIterator(Object cls, Object sequence, int lengthHint) {
        return trace(new PSequenceReverseIterator(cls, getShape(cls), sequence, lengthHint));
    }

    public PIntRangeIterator createIntRangeIterator(PIntRange fastRange) {
        return createIntRangeIterator(fastRange.getIntStart(), fastRange.getIntStep(), fastRange.getIntLength());
    }

    public PIntRangeIterator createIntRangeIterator(int start, int step, int len) {
        return trace(new PIntRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), start, step, len));
    }

    public PBigRangeIterator createBigRangeIterator(PInt start, PInt step, PInt len) {
        return trace(new PBigRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), start, step, len));
    }

    public PBigRangeIterator createBigRangeIterator(PBigRange longRange) {
        return createBigRangeIterator(longRange.getPIntStart(), longRange.getPIntStep(), longRange.getPIntLength());
    }

    public PBigRangeIterator createBigRangeIterator(BigInteger start, BigInteger step, BigInteger len) {
        return createBigRangeIterator(createInt(start), createInt(step), createInt(len));
    }

    public PArrayIterator createArrayIterator(PArray array) {
        return trace(new PArrayIterator(PythonBuiltinClassType.PArrayIterator, PythonBuiltinClassType.PArrayIterator.getInstanceShape(getLanguage()), array));
    }

    public PBaseSetIterator createBaseSetIterator(PBaseSet set, HashingStorageIterator<Object> iterator, int initialSize) {
        return trace(new PBaseSetIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), set, iterator, initialSize));
    }

    public PDictItemIterator createDictItemIterator(HashingStorageIterator<DictEntry> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictItemIterator(PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictItemIterator.getInstanceShape(getLanguage()), iterator, hashingStorage, initialSize));
    }

    public PDictItemIterator createDictReverseItemIterator(HashingStorageIterator<DictEntry> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictItemIterator(PythonBuiltinClassType.PDictReverseItemIterator, PythonBuiltinClassType.PDictReverseItemIterator.getInstanceShape(getLanguage()), iterator, hashingStorage,
                        initialSize));
    }

    public PDictKeyIterator createDictKeyIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictKeyIterator(PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictKeyIterator.getInstanceShape(getLanguage()), iterator, hashingStorage, initialSize));
    }

    public PDictKeyIterator createDictReverseKeyIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictKeyIterator(PythonBuiltinClassType.PDictReverseKeyIterator, PythonBuiltinClassType.PDictReverseKeyIterator.getInstanceShape(getLanguage()), iterator, hashingStorage,
                        initialSize));
    }

    public PDictValueIterator createDictValueIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictValueIterator(PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictValueIterator.getInstanceShape(getLanguage()), iterator, hashingStorage,
                        initialSize));
    }

    public PDictValueIterator createDictReverseValueIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictValueIterator(PythonBuiltinClassType.PDictReverseValueIterator, PythonBuiltinClassType.PDictReverseValueIterator.getInstanceShape(getLanguage()), iterator,
                        hashingStorage,
                        initialSize));
    }

    public Object createSentinelIterator(Object callable, Object sentinel) {
        return trace(new PSentinelIterator(PythonBuiltinClassType.PSentinelIterator, PythonBuiltinClassType.PSentinelIterator.getInstanceShape(getLanguage()), callable, sentinel));
    }

    public PEnumerate createEnumerate(Object cls, Object iterator, long start) {
        return trace(new PEnumerate(cls, getShape(cls), iterator, start));
    }

    public PEnumerate createEnumerate(Object cls, Object iterator, PInt start) {
        return trace(new PEnumerate(cls, getShape(cls), iterator, start));
    }

    public PMap createMap(Object cls) {
        return trace(new PMap(cls, getShape(cls)));
    }

    public PZip createZip(Object cls, Object[] iterables) {
        return trace(new PZip(cls, getShape(cls), iterables));
    }

    public PForeignArrayIterator createForeignArrayIterator(Object iterable) {
        return trace(new PForeignArrayIterator(PythonBuiltinClassType.PForeignArrayIterator, PythonBuiltinClassType.PForeignArrayIterator.getInstanceShape(getLanguage()), iterable));
    }

    public PBuffer createBuffer(Object cls, Object iterable, boolean readonly) {
        return trace(new PBuffer(cls, getShape(cls), iterable, readonly));
    }

    public PBuffer createBuffer(Object iterable, boolean readonly) {
        return trace(new PBuffer(PythonBuiltinClassType.PBuffer, PythonBuiltinClassType.PBuffer.getInstanceShape(getLanguage()), iterable, readonly));
    }

    public PCode createCode(RootCallTarget ct) {
        return trace(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(getLanguage()), ct));
    }

    public PCode createCode(RootCallTarget ct, byte[] codestring, int flags, int firstlineno, byte[] lnotab) {
        return trace(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(getLanguage()), ct, codestring, flags, firstlineno, lnotab));
    }

    public PCode createCode(Object cls, RootCallTarget callTarget, Signature signature,
                    int nlocals, int stacksize, int flags,
                    byte[] codestring, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    byte[] lnotab) {
        return trace(new PCode(cls, getShape(cls), callTarget, signature,
                        nlocals, stacksize, flags,
                        codestring, constants, names,
                        varnames, freevars, cellvars,
                        filename, name, firstlineno, lnotab));
    }

    public PZipImporter createZipImporter(Object cls, PDict zipDirectoryCache, String separator) {
        return trace(new PZipImporter(cls, getShape(cls), zipDirectoryCache, separator));
    }

    /*
     * Socket
     */

    public PSocket createSocket(int family, int type, int proto) {
        return trace(new PSocket(PythonBuiltinClassType.PSocket, PythonBuiltinClassType.PSocket.getInstanceShape(getLanguage()), family, type, proto));
    }

    public PSocket createSocket(Object cls, int family, int type, int proto) {
        return trace(new PSocket(cls, getShape(cls), family, type, proto));
    }

    public PSocket createSocket(Object cls, int family, int type, int proto, int fileno) {
        return trace(new PSocket(cls, getShape(cls), family, type, proto, fileno));
    }

    /*
     * Threading
     */

    public PLock createLock() {
        return createLock(PythonBuiltinClassType.PLock);
    }

    public PLock createLock(Object cls) {
        return trace(new PLock(cls, getShape(cls)));
    }

    public PRLock createRLock() {
        return createRLock(PythonBuiltinClassType.PRLock);
    }

    public PRLock createRLock(Object cls) {
        return trace(new PRLock(cls, getShape(cls)));
    }

    public PThread createPythonThread(Thread thread) {
        return trace(new PThread(PythonBuiltinClassType.PThread, PythonBuiltinClassType.PThread.getInstanceShape(getLanguage()), thread));
    }

    public PThread createPythonThread(Object cls, Thread thread) {
        return trace(new PThread(cls, getShape(cls), thread));
    }

    public PSemLock createSemLock(Object cls, String name, int kind, Semaphore sharedSemaphore) {
        return trace(new PSemLock(cls, getShape(cls), name, kind, sharedSemaphore));
    }

    public PScandirIterator createScandirIterator(PythonContext context, Object dirStream, PosixFileHandle path) {
        return trace(new PScandirIterator(PythonBuiltinClassType.PScandirIterator, PythonBuiltinClassType.PScandirIterator.getInstanceShape(getLanguage()), context, dirStream, path));
    }

    public PDirEntry createDirEntry(Object dirEntryData, PosixFileHandle path) {
        return trace(new PDirEntry(PythonBuiltinClassType.PDirEntry, PythonBuiltinClassType.PDirEntry.getInstanceShape(getLanguage()), dirEntryData, path));
    }

    public PMMap createMMap(SeekableByteChannel channel, long length, long offset) {
        return trace(new PMMap(PythonBuiltinClassType.PMMap, PythonBuiltinClassType.PMMap.getInstanceShape(getLanguage()), channel, length, offset));
    }

    public PMMap createMMap(Object clazz, SeekableByteChannel channel, long length, long offset) {
        return trace(new PMMap(clazz, getShape(clazz), channel, length, offset));
    }

    public BZ2Object.BZ2Compressor createBZ2Compressor(Object clazz) {
        return trace(BZ2Object.createCompressor(clazz, getShape(clazz)));
    }

    public BZ2Object.BZ2Decompressor createBZ2Decompressor(Object clazz) {
        return trace(BZ2Object.createDecompressor(clazz, getShape(clazz)));
    }

    public ZLibCompObject createJavaZLibCompObject(Object clazz, Object stream, int level, int wbits, int strategy, byte[] zdict) {
        return trace(ZLibCompObject.createJava(clazz, getShape(clazz), stream, level, wbits, strategy, zdict));
    }

    public ZLibCompObject createJavaZLibCompObject(Object clazz, Object stream, int wbits, byte[] zdict) {
        return trace(ZLibCompObject.createJava(clazz, getShape(clazz), stream, wbits, zdict));
    }

    public ZLibCompObject createNativeZLibCompObject(Object clazz, Object zst, NFIZlibSupport zlibSupport) {
        return trace(ZLibCompObject.createNative(clazz, getShape(clazz), zst, zlibSupport));
    }

    public PLZMADecompressor createLZMADecompressor(Object clazz, int format, int memlimit) {
        return trace(new PLZMADecompressor(clazz, getShape(clazz), format, memlimit));
    }

    public PLZMACompressor createLZMACompressor(Object clazz, FinishableOutputStream lzmaStream, ByteArrayOutputStream bos) {
        return trace(new PLZMACompressor(clazz, getShape(clazz), lzmaStream, bos));
    }

    public PFileIO createFileIO(Object clazz) {
        return trace(PFileIO.createFileIO(clazz, getShape(clazz)));
    }

    public PBuffered createBufferedReader(Object clazz) {
        return trace(PBuffered.createBufferedReader(clazz, getShape(clazz)));
    }

    public PBuffered createBufferedWriter(Object clazz) {
        return trace(PBuffered.createBufferedWriter(clazz, getShape(clazz)));
    }

    public PBuffered createBufferedRandom(Object clazz) {
        return trace(PBuffered.createBufferedRandom(clazz, getShape(clazz)));
    }
}
