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

#include <hpy.h>
#include <jni.h>
#include <stdint.h>

#include "debug_internal.h"
#include "hpy_native_fast_paths.h"

#define DO_UPCALL_HPY(jni_ctx, name, ...) ((HPy){(HPy_ssize_t)(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)})
#define DO_UPCALL_HPY0(jni_ctx, name) ((HPy){(HPy_ssize_t)(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name)})
#define DO_UPCALL_TRACKER(jni_ctx, name, ...) ((HPyTracker){(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)})
#define DO_UPCALL_HPYTHREADSTATE0(jni_ctx, name) ((HPyThreadState){(*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name)})
#define DO_UPCALL_PTR(jni_ctx, name, ...) (void*) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_PTR_NOARGS(jni_ctx, name) (void*) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name)
#define DO_UPCALL_SIZE_T(jni_ctx, name, ...) (HPy_ssize_t) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_INT(jni_ctx, name, ...) (int) (*jniEnv)->CallIntMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_DOUBLE(jni_ctx, name, ...) (double) (*jniEnv)->CallDoubleMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_LONG(jni_ctx, name, ...) (long) (*jniEnv)->CallLongMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_VOID(jni_ctx, name, ...) (*jniEnv)->CallVoidMethod(jniEnv, (jni_ctx), jniMethod_ ## name, __VA_ARGS__)
#define DO_UPCALL_VOID0(jni_ctx, name) (*jniEnv)->CallVoidMethod(jniEnv, (jni_ctx), jniMethod_ ## name)
#define DO_UPCALL_HPY_SSIZE_T DO_UPCALL_LONG
#define DO_UPCALL_HPY_HASH_T DO_UPCALL_LONG
#define DO_UPCALL_HPY_UCS4 DO_UPCALL_LONG
#define DO_UPCALL_UNSIGNED_LONG DO_UPCALL_LONG
#define DO_UPCALL_LONG_LONG DO_UPCALL_LONG
#define DO_UPCALL_UNSIGNED_LONG_LONG DO_UPCALL_LONG

#define HPY_UP(_h) ((jlong)((_h)._i))
#define PTR_UP(_h) ((jlong)_h)
#define INT_UP(_h) ((jint)_h)
#define LONG_UP(_h) ((jlong)_h)
#define DOUBLE_UP(_h) ((jdouble)_h)
#define SIZE_T_UP(_h) ((jlong)_h)
#define TRACKER_UP(_h) ((jlong)((_h)._i))

static inline HPy _jlong2h(jlong obj) {
    return (HPy){(HPy_ssize_t)obj};
}

static inline jlong _h2jlong(HPy h) {
    return (jlong)(h._i);
}

static inline DHPy _jlong2dh(HPyContext *dctx, jlong obj)
{
    return DHPy_open(dctx, _jlong2h(obj));
}

static inline jlong _dh2jlong(HPyContext *dctx, DHPy dh)
{
    return _h2jlong(DHPy_unwrap(dctx, dh));
}

static inline jlong from_dh(HPyContext *dctx, DHPy dh_result)
{
    jlong result = _dh2jlong(dctx, dh_result);
    DHPy_close(dctx, dh_result);
    return result;
}

/* Copies everything from 'src' to 'dest' and unwraps the 'obj' debug handle. */
static inline void
_buffer_d2u(HPyContext *dctx, const DHPy_buffer *src, UHPy_buffer *dest)
{
    dest->buf = src->buf;
    dest->obj = DHPy_unwrap(dctx, src->obj);
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->ndim = src->ndim;
    dest->format = src->format;
    dest->shape = src->shape;
    dest->strides = src->strides;
    dest->suboffsets = src->suboffsets;
    dest->internal = src->internal;
}

/* Copies everything from 'src' to 'dest' and opens a debug handle for 'obj'. */
static inline void
_buffer_u2d(HPyContext *dctx, const UHPy_buffer *src, DHPy_buffer *dest)
{
    dest->buf = src->buf;
    dest->obj = DHPy_open(dctx, src->obj);
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->ndim = src->ndim;
    dest->format = src->format;
    dest->shape = src->shape;
    dest->strides = src->strides;
    dest->suboffsets = src->suboffsets;
    dest->internal = src->internal;
}

/* just for better readability */
typedef HPy_buffer DHPy_buffer;
typedef HPy_buffer UHPy_buffer;

#define CONTEXT_INSTANCE(_hpy_ctx) ((jobject)(graal_hpy_context_get_native_context(_hpy_ctx)->jni_context))

_HPy_HIDDEN extern JNIEnv* jniEnv;

_HPy_HIDDEN HPy upcallTupleFromArray(HPyContext *ctx, HPy *items, HPy_ssize_t nitems, bool steal);

_HPy_HIDDEN void upcallBulkClose(HPyContext *ctx, HPy *items, HPy_ssize_t nitems);

_HPy_HIDDEN int hpy_debug_ctx_init(HPyContext *dctx, HPyContext *uctx);

_HPy_HIDDEN void hpy_debug_ctx_free(HPyContext *dctx);
