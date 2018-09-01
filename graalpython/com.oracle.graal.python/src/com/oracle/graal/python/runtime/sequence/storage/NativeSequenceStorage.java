/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.truffle.api.CompilerAsserts;

public class NativeSequenceStorage extends SequenceStorage {

    /* native pointer object */
    private Object ptr;

    /* length of contents */
    protected int length;

    /* allocated capacity */
    protected int capacity;

    protected final ListStorageType elementType;

    public NativeSequenceStorage(Object ptr, int length, int capacity, ListStorageType elementType) {
        this.ptr = ptr;
        this.capacity = capacity;
        this.length = length;
        this.elementType = elementType;
    }

    public Object getPtr() {
        return ptr;
    }

    public void setPtr(Object ptr) {
        this.ptr = ptr;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public ListStorageType getElementType() {
        return elementType;
    }

    @Override
    public final int length() {
        return length;
    }

    @Override
    public void setNewLength(int length) {
        assert length <= capacity;
        this.length = length;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<NativeSequenceStorage(type=%s, len=%d, cap=%d) at %s>", elementType, length, capacity, ptr);
    }

    /**
     * Ensure that the current capacity is big enough. If not, we increase capacity to the next
     * designated size (not necessarily the requested one).
     */
    @Override
    public void ensureCapacity(int newCapacity) {
        throw new AssertionError("should not reach");
    }

    @Override
    public int index(Object value) {
        throw new AssertionError("should not reach");
    }

    @Override
    public SequenceStorage copy() {
        throw new AssertionError("should not reach");
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        throw new AssertionError("should not reach");
    }

    @Override
    public Object[] getInternalArray() {
        throw new AssertionError("should not reach");
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        throw new AssertionError("should not reach");
    }

    @Override
    public Object getItemNormalized(int idx) {
        throw new AssertionError("should not reach");
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        throw new AssertionError("should not reach");
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        throw new AssertionError("should not reach");
    }

    @Override
    public SequenceStorage getSliceInBound(int start, int stop, int step, int len) {
        throw new AssertionError("should not reach");
    }

    @Override
    public void delItemInBound(int idx) {
        throw new AssertionError("should not reach");
    }

    @Override
    public Object popInBound(int idx) {
        throw new AssertionError("should not reach");
    }

    @Override
    public void reverse() {
        throw new AssertionError("should not reach");
    }

    @Override
    public boolean equals(SequenceStorage other) {
        CompilerAsserts.neverPartOfCompilation();
        throw new AssertionError("should not reach");
    }

    @Override
    public SequenceStorage generalizeFor(Object value, SequenceStorage other) {
        throw new AssertionError("should not reach");
    }

    @Override
    public Object getIndicativeValue() {
        throw new AssertionError("should not reach");
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        throw new AssertionError("should not reach");
    }

    @Override
    public Object getInternalArrayObject() {
        return ptr;
    }
}
