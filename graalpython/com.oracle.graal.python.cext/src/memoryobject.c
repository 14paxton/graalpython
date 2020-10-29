/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "capi.h"

PyTypeObject PyMemoryView_Type = PY_TRUFFLE_TYPE_WITH_ITEMSIZE("memoryview", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, offsetof(PyMemoryViewObject, ob_array), sizeof(Py_ssize_t));
PyTypeObject PyBuffer_Type = PY_TRUFFLE_TYPE("buffer", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyBufferDecorator));

int bufferdecorator_getbuffer(PyBufferDecorator *self, Py_buffer *view, int flags) {
    return PyBuffer_FillInfo(view, (PyObject*)self, polyglot_get_member(self, "buf_delegate"), PyObject_Size((PyObject *)self) * sizeof(PyObject*), self->readonly, flags);
}

PyObject * PyMemoryView_FromObject(PyObject *v) {
	// TODO(fa): This needs to be fixed. The actual implementation is located in
	// '_memoryview.c'. However, the current way we use it does not allow C exts
	// to link to it. We need to restructure this.
	return NULL;
}

PyObject* PyMemoryView_FromBuffer(Py_buffer *buffer) {
	Py_ssize_t ndim = buffer->ndim;
	return polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_WrapBuffer",
			buffer,
			native_to_java(buffer->obj),
			buffer->len,
			buffer->readonly,
			buffer->itemsize,
			polyglot_from_string(buffer->format ? buffer->format : "B", "ascii"),
			buffer->ndim,
			polyglot_from_i8_array(buffer->buf, buffer->len),
			// TODO 32 bit?
			buffer->shape ? polyglot_from_i64_array(buffer->shape, ndim) : NULL,
			buffer->strides ? polyglot_from_i64_array(buffer->strides, ndim) : NULL,
			buffer->suboffsets ? polyglot_from_i64_array(buffer->suboffsets, ndim) : NULL);
}
