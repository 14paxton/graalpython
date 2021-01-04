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
package com.oracle.graal.python.builtins.objects.dict;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

public abstract class PDictView extends PythonBuiltinObject {
    private final PHashingCollection dict;
    private final String name;

    public PDictView(Object clazz, Shape instanceShape, String name, PHashingCollection dict) {
        super(clazz, instanceShape);
        this.name = name;
        assert dict != null;
        this.dict = dict;
    }

    public final PHashingCollection getWrappedDict() {
        return dict;
    }

    public String getName() {
        return name;
    }

    public abstract static class PBaseDictIterator<T> extends PHashingStorageIterator<T> {

        protected final HashingStorage hashingStorage;

        public PBaseDictIterator(Object clazz, Shape instanceShape, HashingStorageIterator<T> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, instanceShape, iterator, initialSize);
            this.hashingStorage = hashingStorage;
        }

        public HashingStorage getHashingStorage() {
            return hashingStorage;
        }

        public Object next(@SuppressWarnings("unused") PythonObjectFactory factory) {
            return this.next();
        }

        public final boolean checkSizeChanged(HashingStorageLibrary lib) {
            return lib.length(hashingStorage) != size;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // the keys
    //
    // -----------------------------------------------------------------------------------------------------------------
    public static final class PDictKeyIterator extends PBaseDictIterator<Object> {
        public PDictKeyIterator(Object clazz, Shape instanceShape, HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, instanceShape, iterator, hashingStorage, initialSize);
        }
    }

    @ExportLibrary(PythonObjectLibrary.class)
    public static final class PDictKeysView extends PDictView {

        public PDictKeysView(Object clazz, Shape instanceShape, PHashingCollection dict) {
            super(clazz, instanceShape, "dict_keys", dict);
        }

        /* this is correct because it cannot be subclassed in Python */
        @ExportMessage(limit = "getCallSiteInlineCacheMaxDepth()")
        @SuppressWarnings("static-method")
        Object getIteratorWithState(@SuppressWarnings("unused") ThreadState threadState,
                        @Cached @SuppressWarnings("unused") HashingCollectionNodes.GetDictStorageNode getStore,
                        @Bind("getStore.execute(this.getWrappedDict())") HashingStorage storage,
                        @CachedLibrary("storage") HashingStorageLibrary lib,
                        @Cached PythonObjectFactory factory) {
            return factory.createDictKeyIterator(lib.keys(storage).iterator(), storage, lib.length(storage));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // the values
    //
    // -----------------------------------------------------------------------------------------------------------------
    public static final class PDictValueIterator extends PBaseDictIterator<Object> {
        public PDictValueIterator(Object clazz, Shape instanceShape, HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, instanceShape, iterator, hashingStorage, initialSize);
        }
    }

    @ExportLibrary(PythonObjectLibrary.class)
    public static final class PDictValuesView extends PDictView {

        public PDictValuesView(Object clazz, Shape instanceShape, PHashingCollection dict) {
            super(clazz, instanceShape, "dict_values", dict);
        }

        /* this is correct because it cannot be subclassed in Python */
        @ExportMessage(limit = "getCallSiteInlineCacheMaxDepth()")
        @SuppressWarnings("static-method")
        Object getIteratorWithState(@SuppressWarnings("unused") ThreadState threadState,
                        @Cached @SuppressWarnings("unused") HashingCollectionNodes.GetDictStorageNode getStore,
                        @Bind("getStore.execute(this.getWrappedDict())") HashingStorage storage,
                        @CachedLibrary("storage") HashingStorageLibrary lib,
                        @Cached PythonObjectFactory factory) {
            return factory.createDictValueIterator(lib.values(storage).iterator(), storage, lib.length(storage));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // the items
    //
    // -----------------------------------------------------------------------------------------------------------------
    public static final class PDictItemIterator extends PBaseDictIterator<DictEntry> {
        public PDictItemIterator(Object clazz, Shape instanceShape, HashingStorageIterator<DictEntry> iterator, HashingStorage hashingStorage, int initialSize) {
            super(clazz, instanceShape, iterator, hashingStorage, initialSize);
        }

        @TruffleBoundary
        private DictEntry nextVal() {
            return (DictEntry) super.next();
        }

        @Override
        public Object next(PythonObjectFactory factory) {
            DictEntry value = nextVal();
            return factory.createTuple(new Object[]{value.getKey(), value.getValue()});
        }
    }

    @ExportLibrary(PythonObjectLibrary.class)
    public static final class PDictItemsView extends PDictView {

        public PDictItemsView(Object clazz, Shape instanceShape, PHashingCollection dict) {
            super(clazz, instanceShape, "dict_items", dict);
        }

        /* this is correct because it cannot be subclassed in Python */
        @ExportMessage(limit = "getCallSiteInlineCacheMaxDepth()")
        @SuppressWarnings("static-method")
        Object getIteratorWithState(@SuppressWarnings("unused") ThreadState threadState,
                        @Cached @SuppressWarnings("unused") HashingCollectionNodes.GetDictStorageNode getStore,
                        @Bind("getStore.execute(this.getWrappedDict())") HashingStorage storage,
                        @CachedLibrary("storage") HashingStorageLibrary lib,
                        @Cached PythonObjectFactory factory) {
            return factory.createDictItemIterator(lib.entries(storage).iterator(), storage, lib.length(storage));
        }
    }
}
