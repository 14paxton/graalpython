/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import java.util.Iterator;

import org.graalvm.collections.MapCursor;

import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage.DictKey;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Based on @see org.graalvm.collections.EconomicMapImpl
 */
@SuppressWarnings("javadoc")
final class PEMap implements Iterable<DictKey> {

    /**
     * Initial number of key/value pair entries that is allocated in the first entries array.
     */
    private static final int INITIAL_CAPACITY = 4;

    /**
     * Initial number of key/value pair entries that is allocated in the first entries array if the
     * requested size is too large. Set to 2 MB
     */
    private static final int OBJ_ARRAY_SIZE_1_MB = 131072;
    private static final int MAX_INITIAL_CAPACITY = OBJ_ARRAY_SIZE_1_MB * 2;

    /**
     * Maximum number of entries that are moved linearly forward if a key is removed.
     */
    private static final int COMPRESS_IMMEDIATE_CAPACITY = 8;

    /**
     * Minimum number of key/value pair entries added when the entries array is increased in size.
     */
    private static final int MIN_CAPACITY_INCREASE = 8;

    /**
     * Number of entries above which a hash table is created.
     */
    private static final int HASH_THRESHOLD = 4;

    /**
     * Maximum number of entries allowed in the map.
     */
    private static final int MAX_ELEMENT_COUNT = Integer.MAX_VALUE >> 1;

    /**
     * Number of entries above which more than 1 byte is necessary for the hash index.
     */
    private static final int LARGE_HASH_THRESHOLD = ((1 << Byte.SIZE) << 1);

    /**
     * Number of entries above which more than 2 bytes are are necessary for the hash index.
     */
    private static final int VERY_LARGE_HASH_THRESHOLD = (LARGE_HASH_THRESHOLD << Byte.SIZE);

    /**
     * Total number of entries (actual entries plus deleted entries).
     */
    private int totalEntries;

    /**
     * Number of deleted entries.
     */
    private int deletedEntries;

    /**
     * Entries array with even indices storing keys and odd indices storing values.
     */
    private Object[] entries;

    /**
     * Hash array that is interpreted either as byte or short or int array depending on number of
     * map entries.
     */
    private byte[] hashArray;

    private boolean hasSideEffect;

    /**
     * Intercept method for debugging purposes.
     */
    private static PEMap intercept(PEMap map) {
        return map;
    }

    public static PEMap create(boolean isSet, boolean hasSideEffects) {
        return intercept(new PEMap(isSet, hasSideEffects));
    }

    public static PEMap create(int initialCapacity, boolean isSet, boolean hasSideEffects) {
        return intercept(new PEMap(initialCapacity, isSet, hasSideEffects));
    }

    private PEMap(boolean isSet, boolean hasSideEffect) {
        this.isSet = isSet;
        this.hasSideEffect = hasSideEffect;
    }

    private PEMap(int initialCapacity, boolean isSet, boolean hasSideEffect) {
        this(isSet, hasSideEffect);
        init(initialCapacity);
    }

    private void init(int size) {
        if (size > INITIAL_CAPACITY) {
            int cap = size << 1;
            if (cap < 0 || cap > MAX_INITIAL_CAPACITY) {
                cap = MAX_INITIAL_CAPACITY;
            }
            entries = new Object[cap];
        }
    }

    protected boolean hasSideEffect() {
        return hasSideEffect;
    }

    /**
     * Links the collisions. Needs to be immutable class for allowing efficient shallow copy from
     * other map on construction.
     */
    private static final class CollisionLink {

        CollisionLink(Object value, int next) {
            this.value = value;
            this.next = next;
        }

        final Object value;

        /**
         * Index plus one of the next entry in the collision link chain.
         */
        final int next;
    }

    public Object get(VirtualFrame frame, DictKey key, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        if (hasSideEffect) {
            return getSE(frame, key, findProfile, eqNode);
        }
        assert key != null;

        int index = find(frame, key, findProfile, eqNode);
        if (index != -1) {
            return getValue(index);
        }
        return null;
    }

    private int find(VirtualFrame frame, DictKey key, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        if (findProfile.profile(hasHashArray())) {
            return findHash(frame, key, eqNode);
        } else {
            return findLinear(frame, key, eqNode);
        }
    }

    private int findLinear(VirtualFrame frame, DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        for (int i = 0; i < totalEntries; i++) {
            DictKey entryKey = getKey(i);
            if (entryKey != null && compareKeys(frame, key, entryKey, eqNode)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean compareKeys(VirtualFrame frame, DictKey key, DictKey other, PyObjectRichCompareBool.EqNode eqNode) {
        // Comparison as per CPython's dictobject.c#lookdict function. First
        // check if the keys are identical, then check if the hashes are the
        // same, and only if they are, also call the comparison function.
        if (key.value == other.value) {
            return true;
        }
        if (key.hash == other.hash) {
            return eqNode.execute(frame, other.value, key.value);
        }
        return false;
    }

    private int findHash(VirtualFrame frame, DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        int index = getHashArray(getHashIndex(key)) - 1;
        if (index != -1) {
            DictKey entryKey = getKey(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findWithCollision(frame, key, (CollisionLink) entryValue, eqNode);
                }
            }
        }

        return -1;
    }

    private int findWithCollision(VirtualFrame frame, DictKey key, CollisionLink initialEntryValue, PyObjectRichCompareBool.EqNode eqNode) {
        int index;
        DictKey entryKey;
        CollisionLink entryValue = initialEntryValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashArray(int index) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return (hashArray[index] & 0xFF);
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8);
        } else {
            int adjustedIndex = index << 2;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8) | ((hashArray[adjustedIndex + 2] & 0xFF) << 16) | ((hashArray[adjustedIndex + 3] & 0xFF) << 24);
        }
    }

    private void setHashArray(int index, int value) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            hashArray[index] = (byte) value;
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            hashArray[adjustedIndex] = (byte) value;
            hashArray[adjustedIndex + 1] = (byte) (value >> 8);
        } else {
            int adjustedIndex = index << 2;
            hashArray[adjustedIndex] = (byte) value;
            hashArray[adjustedIndex + 1] = (byte) (value >> 8);
            hashArray[adjustedIndex + 2] = (byte) (value >> 16);
            hashArray[adjustedIndex + 3] = (byte) (value >> 24);
        }
    }

    private int findAndRemoveHash(VirtualFrame frame, DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        int hashIndex = getHashIndex(key);
        int index = getHashArray(hashIndex) - 1;
        if (index != -1) {
            DictKey entryKey = getKey(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                Object value = getRawValue(index);
                int nextIndex = -1;
                if (value instanceof CollisionLink) {
                    CollisionLink collisionLink = (CollisionLink) value;
                    nextIndex = collisionLink.next;
                }
                setHashArray(hashIndex, nextIndex + 1);
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findAndRemoveWithCollision(frame, key, (CollisionLink) entryValue, index, eqNode);
                }
            }
        }

        return -1;
    }

    private int findAndRemoveWithCollision(VirtualFrame frame, DictKey key, CollisionLink initialEntryValue, int initialIndexValue, PyObjectRichCompareBool.EqNode eqNode) {
        int index;
        DictKey entryKey;
        CollisionLink entryValue = initialEntryValue;
        int lastIndex = initialIndexValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    CollisionLink thisCollisionLink = (CollisionLink) value;
                    setRawValue(lastIndex, new CollisionLink(collisionLink.value, thisCollisionLink.next));
                } else {
                    setRawValue(lastIndex, collisionLink.value);
                }
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                    lastIndex = index;
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashIndex(DictKey key) {
        int hash = key.hashCode();
        hash = hash ^ (hash >>> 16);
        return hash & (getHashTableSize() - 1);
    }

    public Object put(VirtualFrame frame, DictKey key, Object value, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        if (hasSideEffect) {
            return putSE(frame, key, value, findProfile, eqNode);
        }
        if (key == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index = find(frame, key, findProfile, eqNode);
        if (index != -1) {
            Object oldValue = getValue(index);
            setValue(index, value);
            return oldValue;
        }

        put(key, value);
        return null;
    }

    public void put(DictKey key, Object value) {

        int nextEntryIndex = totalEntries;
        if (entries == null) {
            entries = new Object[INITIAL_CAPACITY << 1];
        } else if (entries.length == nextEntryIndex << 1) {
            grow();

            assert entries.length > totalEntries << 1;
            // Can change if grow is actually compressing.
            nextEntryIndex = totalEntries;
        }

        setKey(nextEntryIndex, key);
        setValue(nextEntryIndex, value);
        totalEntries++;

        if (hasHashArray()) {
            // Rehash on collision if hash table is more than three quarters full.
            boolean rehashOnCollision = (getHashTableSize() < (size() + (size() >> 1)));
            putHashEntry(key, nextEntryIndex, rehashOnCollision);
        } else if (totalEntries > getHashThreshold()) {
            createHash();
        }
    }

    @TruffleBoundary
    protected void putAll(PEMap other) {
        MapCursor<DictKey, Object> e = other.getEntries();
        while (e.advance()) {
            put(e.getKey(), e.getValue());
        }
    }

    @TruffleBoundary
    protected void putAll(PEMap other, PyObjectRichCompareBool.EqNode eqNode) {
        MapCursor<DictKey, Object> e = other.getEntries();
        while (e.advance()) {
            put(null, e.getKey(), e.getValue(), ConditionProfile.getUncached(), eqNode);
        }
    }

    /**
     * Number of entries above which a hash table should be constructed.
     */
    private static int getHashThreshold() {
        return HASH_THRESHOLD;
    }

    private void grow() {
        int entriesLength = entries.length;
        int newSize = (entriesLength >> 1) + Math.max(MIN_CAPACITY_INCREASE, entriesLength >> 2);
        if (newSize > MAX_ELEMENT_COUNT) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException("map grown too large!");
        }
        Object[] newEntries = new Object[newSize << 1];
        PythonUtils.arraycopy(entries, 0, newEntries, 0, entriesLength);
        entries = newEntries;
        if ((entriesLength < LARGE_HASH_THRESHOLD && newEntries.length >= LARGE_HASH_THRESHOLD) ||
                        (entriesLength < VERY_LARGE_HASH_THRESHOLD && newEntries.length > VERY_LARGE_HASH_THRESHOLD)) {
            // Rehash in order to change number of bits reserved for hash indices.
            createHash();
        }
    }

    /**
     * Compresses the graph if there is a large number of deleted entries and returns the translated
     * new next index.
     */
    private int maybeCompress(int nextIndex) {
        if (entries.length != INITIAL_CAPACITY << 1 && deletedEntries >= (totalEntries >> 1) + (totalEntries >> 2)) {
            return compressLarge(nextIndex);
        }
        return nextIndex;
    }

    /**
     * Compresses the graph and returns the translated new next index.
     */
    private int compressLarge(int nextIndex) {
        int size = INITIAL_CAPACITY;
        int remaining = totalEntries - deletedEntries;

        while (size <= remaining) {
            size += Math.max(MIN_CAPACITY_INCREASE, size >> 1);
        }

        Object[] newEntries = new Object[size << 1];
        int z = 0;
        int newNextIndex = remaining;
        for (int i = 0; i < totalEntries; ++i) {
            DictKey key = getKey(i);
            if (i == nextIndex) {
                newNextIndex = z;
            }
            if (key != null) {
                newEntries[z << 1] = key;
                newEntries[(z << 1) + 1] = getValue(i);
                z++;
            }
        }

        this.entries = newEntries;
        totalEntries = z;
        deletedEntries = 0;
        if (z <= getHashThreshold()) {
            this.hashArray = null;
        } else {
            createHash();
        }
        return newNextIndex;
    }

    private int getHashTableSize() {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return hashArray.length;
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            return hashArray.length >> 1;
        } else {
            return hashArray.length >> 2;
        }
    }

    private void createHash() {
        int entryCount = size();

        // Calculate smallest 2^n that is greater number of entries.
        int size = getHashThreshold();
        while (size <= entryCount) {
            size <<= 1;
        }

        // Give extra size to avoid collisions.
        size <<= 1;

        if (this.entries.length >= VERY_LARGE_HASH_THRESHOLD) {
            // Every entry has 4 bytes.
            size <<= 2;
        } else if (this.entries.length >= LARGE_HASH_THRESHOLD) {
            // Every entry has 2 bytes.
            size <<= 1;
        } else {
            // Entries are very small => give extra size to further reduce collisions.
            size <<= 1;
        }

        hashArray = new byte[size];
        for (int i = 0; i < totalEntries; i++) {
            DictKey entryKey = getKey(i);
            if (entryKey != null) {
                putHashEntry(entryKey, i, false);
            }
        }
    }

    private void putHashEntry(DictKey key, int entryIndex, boolean rehashOnCollision) {
        int hashIndex = getHashIndex(key);
        int oldIndex = getHashArray(hashIndex) - 1;
        if (oldIndex != -1 && rehashOnCollision) {
            this.createHash();
            return;
        }
        setHashArray(hashIndex, entryIndex + 1);
        Object value = getRawValue(entryIndex);
        if (oldIndex != -1) {
            assert entryIndex != oldIndex : "this cannot happend and would create an endless collision link cycle";
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, new CollisionLink(collisionLink.value, oldIndex));
            } else {
                setRawValue(entryIndex, new CollisionLink(getRawValue(entryIndex), oldIndex));
            }
        } else {
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, collisionLink.value);
            }
        }
    }

    public int size() {
        return totalEntries - deletedEntries;
    }

    public boolean containsKey(VirtualFrame frame, DictKey key, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        return find(frame, key, findProfile, eqNode) != -1;
    }

    public void clear() {
        entries = null;
        hashArray = null;
        totalEntries = deletedEntries = 0;
    }

    private boolean hasHashArray() {
        return hashArray != null;
    }

    @TruffleBoundary
    public Object removeKey(DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        if (key == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index;
        if (hasHashArray()) {
            index = this.findAndRemoveHash(null, key, eqNode);
        } else {
            index = this.findLinear(null, key, eqNode);
        }

        if (index != -1) {
            Object value = getValue(index);
            remove(index);
            return value;
        }
        return null;
    }

    /**
     * Removes the element at the specific index and returns the index of the next element. This can
     * be a different value if graph compression was triggered.
     */
    private int remove(int indexToRemove) {
        int index = indexToRemove;
        int entriesAfterIndex = totalEntries - index - 1;
        int result = index + 1;

        // Without hash array, compress immediately.
        if (entriesAfterIndex <= COMPRESS_IMMEDIATE_CAPACITY && !hasHashArray()) {
            while (index < totalEntries - 1) {
                setKey(index, getKey(index + 1));
                setRawValue(index, getRawValue(index + 1));
                index++;
            }
            result--;
        }

        setKey(index, null);
        setRawValue(index, null);
        if (index == totalEntries - 1) {
            // Make sure last element is always non-null.
            totalEntries--;
            while (index > 0 && getKey(index - 1) == null) {
                totalEntries--;
                deletedEntries--;
                index--;
            }
        } else {
            deletedEntries++;
            result = maybeCompress(result);
        }

        return result;
    }

    abstract class AbstractSparseMapIterator<E> implements Iterator<E> {

        protected int current;

        AbstractSparseMapIterator(int current) {
            this.current = current;
        }

        public int getState() {
            return current;
        }

        public void setState(int state) {
            current = state;
        }

        @Override
        public void remove() {
            if (hasHashArray()) {
                PEMap.this.findAndRemoveHash(null, getKey(current - 1), PyObjectRichCompareBool.EqNode.getUncached());
            }
            current = PEMap.this.remove(current - 1);
        }
    }

    private abstract class SparseMapIterator<E> extends AbstractSparseMapIterator<E> {
        public SparseMapIterator() {
            super(0);
        }

        @Override
        public boolean hasNext() {
            return current < totalEntries;
        }
    }

    private abstract class ReverseSparseMapIterator<E> extends AbstractSparseMapIterator<E> {
        public ReverseSparseMapIterator() {
            super(totalEntries);
        }

        @Override
        public boolean hasNext() {
            return current > 0;
        }
    }

    @TruffleBoundary
    public Iterable<Object> getValues() {
        return new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new SparseMapIterator<Object>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Object next() {
                        Object result;
                        while (true) {
                            result = getValue(current);
                            if (result == null && getKey(current) == null) {
                                // values can be null, double-check if key is also null
                                current++;
                            } else {
                                current++;
                                break;
                            }
                        }
                        return result;
                    }
                };
            }
        };
    }

    public Iterable<DictKey> getKeys() {
        return this;
    }

    public boolean isEmpty() {
        return this.size() == 0;
    }

    @TruffleBoundary
    public MapCursor<DictKey, Object> getEntries() {
        if (hasSideEffect) {
            return getEntriesSE();
        }
        return new MapCursor<DictKey, Object>() {
            int current = -1;

            @Override
            public boolean advance() {
                current++;
                if (current >= totalEntries) {
                    return false;
                } else {
                    while (PEMap.this.getKey(current) == null) {
                        // Skip over null entries
                        current++;
                    }
                    return true;
                }
            }

            @Override
            public DictKey getKey() {
                return PEMap.this.getKey(current);
            }

            @Override
            public Object getValue() {
                return PEMap.this.getValue(current);
            }

            @Override
            public void remove() {
                if (hasHashArray()) {
                    PEMap.this.findAndRemoveHash(null, PEMap.this.getKey(current), PyObjectRichCompareBool.EqNode.getUncached());
                }
                current = PEMap.this.remove(current) - 1;
            }
        };
    }

    private DictKey getKey(int index) {
        return (DictKey) entries[index << 1];
    }

    private void setKey(int index, DictKey newValue) {
        entries[index << 1] = newValue;
    }

    private void setValue(int index, Object newValue) {
        Object oldValue = getRawValue(index);
        if (oldValue instanceof CollisionLink) {
            CollisionLink collisionLink = (CollisionLink) oldValue;
            setRawValue(index, new CollisionLink(newValue, collisionLink.next));
        } else {
            setRawValue(index, newValue);
        }
    }

    private void setRawValue(int index, Object newValue) {
        entries[(index << 1) + 1] = newValue;
    }

    private Object getRawValue(int index) {
        return entries[(index << 1) + 1];
    }

    private Object getValue(int index) {
        Object object = getRawValue(index);
        if (object instanceof CollisionLink) {
            return ((CollisionLink) object).value;
        }
        return object;
    }

    private final boolean isSet;

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder builder = new StringBuilder();
        builder.append(isSet ? "set(size=" : "map(size=").append(size()).append(", {");
        String sep = "";
        MapCursor<DictKey, Object> cursor = getEntries();
        while (cursor.advance()) {
            builder.append(sep);
            if (isSet) {
                builder.append(cursor.getKey());
            } else {
                builder.append("(").append(cursor.getKey()).append(",").append(cursor.getValue()).append(")");
            }
            sep = ",";
        }
        builder.append("})");
        return builder.toString();
    }

    @Override
    public Iterator<DictKey> iterator() {
        return new SparseMapIterator<DictKey>() {
            @Override
            public DictKey next() {
                DictKey result;
                while ((result = getKey(current++)) == null) {
                    // skip null entries
                }
                return result;
            }
        };
    }

    public Iterator<DictKey> reverseKeyIterator() {
        return new ReverseSparseMapIterator<DictKey>() {
            @Override
            public DictKey next() {
                DictKey result;
                while ((result = getKey(--current)) == null) {
                    // skip null entries
                }
                return result;
            }
        };
    }

    protected void setSideEffectFlag() {
        this.hasSideEffect = true;
    }

    private Object getSE(VirtualFrame frame, DictKey key, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        assert key != null;

        Entry p = findSE(frame, key, findProfile, eqNode);
        if (p != null) {
            return p.value;
        }
        return null;
    }

    private Object putSE(VirtualFrame frame, DictKey key, Object value, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        if (key == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException("null not supported as key!");
        }
        Entry entry = findSE(frame, key, findProfile, eqNode);
        if (entry != null) {
            setValue(entry.index, value);
            return entry.value;
        }

        put(key, value);
        return null;
    }

    @TruffleBoundary
    private MapCursor<DictKey, Object> getEntriesSE() {
        return new MapCursor<DictKey, Object>() {
            int current = -1;
            DictKey cachedKey = null;
            Object cachedValue = null;

            @Override
            public boolean advance() {
                current++;
                if (current >= totalEntries) {
                    return false;
                } else {
                    while (PEMap.this.getKey(current) == null) {
                        // Skip over null entries
                        current++;
                    }
                    cachedKey = PEMap.this.getKey(current);
                    cachedValue = PEMap.this.getValue(current);
                    return true;
                }
            }

            @Override
            public DictKey getKey() {
                return cachedKey;
            }

            @Override
            public Object getValue() {
                return cachedValue;
            }

            @Override
            public void remove() {
                if (hasHashArray()) {
                    PEMap.this.findAndRemoveHashSE(null, PEMap.this.getKey(current), PyObjectRichCompareBool.EqNode.getUncached());
                }
                if (totalEntries > 0) { // in case of modifying __eq__,see GR-21141.
                    current = PEMap.this.remove(current) - 1;
                }
            }
        };
    }

    private Entry findSE(VirtualFrame frame, DictKey key, ConditionProfile findProfile, PyObjectRichCompareBool.EqNode eqNode) {
        if (findProfile.profile(hasHashArray())) {
            return findHashSE(frame, key, eqNode);
        } else {
            return findLinearSE(frame, key, eqNode);
        }
    }

    private Entry findLinearSE(VirtualFrame frame, DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        for (int i = 0; i < totalEntries; i++) {
            DictKey entryKey = getKey(i);
            Object value = getValue(i);
            if (entryKey != null && compareKeys(frame, key, entryKey, eqNode)) {
                return new Entry(i, entryKey, value);
            }
        }
        return null;
    }

    private Entry findHashSE(VirtualFrame frame, DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        int index = getHashArray(getHashIndex(key)) - 1;
        if (index != -1) {
            DictKey entryKey = getKey(index);
            Object value = getValue(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                return new Entry(index, entryKey, value);
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findWithCollisionSE(frame, key, (CollisionLink) entryValue, eqNode);
                }
            }
        }

        return null;
    }

    private Entry findWithCollisionSE(VirtualFrame frame, DictKey key, CollisionLink initialEntryValue, PyObjectRichCompareBool.EqNode eqNode) {
        int index;
        DictKey entryKey;
        CollisionLink entryValue = initialEntryValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            Object value = getValue(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                return new Entry(index, entryKey, value);
            } else {
                value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                } else {
                    return null;
                }
            }
        }
    }

    private Entry findAndRemoveHashSE(VirtualFrame frame, DictKey key, PyObjectRichCompareBool.EqNode eqNode) {
        int hashIndex = getHashIndex(key);
        int index = getHashArray(hashIndex) - 1;
        if (index != -1) {
            DictKey entryKey = getKey(index);
            Object value = getRawValue(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                int nextIndex = -1;
                if (value instanceof CollisionLink) {
                    CollisionLink collisionLink = (CollisionLink) value;
                    value = collisionLink.value;
                    nextIndex = collisionLink.next;
                }
                setHashArray(hashIndex, nextIndex + 1);
                return new Entry(index, entryKey, value);
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findAndRemoveWithCollisionSE(frame, key, (CollisionLink) entryValue, index, eqNode);
                }
            }
        }

        return null;
    }

    private Entry findAndRemoveWithCollisionSE(VirtualFrame frame, DictKey key, CollisionLink initialEntryValue, int initialIndexValue, PyObjectRichCompareBool.EqNode eqNode) {
        int index;
        DictKey entryKey;
        CollisionLink entryValue = initialEntryValue;
        int lastIndex = initialIndexValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            final Object value = getRawValue(index);
            if (compareKeys(frame, key, entryKey, eqNode)) {
                if (value instanceof CollisionLink) {
                    CollisionLink thisCollisionLink = (CollisionLink) value;
                    setRawValue(lastIndex, new CollisionLink(collisionLink.value, thisCollisionLink.next));
                } else {
                    setRawValue(lastIndex, collisionLink.value);
                }
                return new Entry(index, entryKey, collisionLink.value);
            } else {
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                    lastIndex = index;
                } else {
                    return null;
                }
            }
        }
    }
}

final class Entry {
    int index;
    DictKey key;
    Object value;

    Entry(int index, DictKey key, Object val) {
        this.index = index;
        this.key = key;
        this.value = val;
    }
}
