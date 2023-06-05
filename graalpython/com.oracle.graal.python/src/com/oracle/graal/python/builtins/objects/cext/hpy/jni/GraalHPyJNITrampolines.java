/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.jni;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class GraalHPyJNITrampolines {

    /* manual HPY JNI trampoline declarations */

    @TruffleBoundary
    public static native long executeModuleInit(long target, long ctx);

    @TruffleBoundary
    public static native long executeDebugModuleInit(long target, long ctx);

    @TruffleBoundary
    public static native void executeDestroyfunc(long target, long dataptr);

    // int (*HPyFunc_traverseproc)(void *, HPyFunc_visitproc, void *);
    public static long executeTraverseproc(long target, long self, long visitproc, long field) {
        throw CompilerDirectives.shouldNotReachHere("traverseproc should never be called");
    }

    /* generated HPY JNI trampoline declarations */

    // {{start autogen}}
    // typedef HPy (*HPyFunc_noargs)(HPyContext *ctx, HPy self)
    @TruffleBoundary
    public static native long executeNoargs(long target, long ctx, long self);

    // typedef HPy (*HPyFunc_o)(HPyContext *ctx, HPy self, HPy arg)
    @TruffleBoundary
    public static native long executeO(long target, long ctx, long self, long arg);

    // typedef HPy (*HPyFunc_varargs)(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs)
    @TruffleBoundary
    public static native long executeVarargs(long target, long ctx, long self, long args, long nargs);

    // typedef HPy (*HPyFunc_keywords)(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs, HPy kw)
    @TruffleBoundary
    public static native long executeKeywords(long target, long ctx, long self, long args, long nargs, long kw);

    // typedef HPy (*HPyFunc_unaryfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeUnaryfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_binaryfunc)(HPyContext *ctx, HPy, HPy)
    @TruffleBoundary
    public static native long executeBinaryfunc(long target, long ctx, long arg0, long arg1);

    // typedef HPy (*HPyFunc_ternaryfunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native long executeTernaryfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_inquiry)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native int executeInquiry(long target, long ctx, long arg0);

    // typedef HPy_ssize_t (*HPyFunc_lenfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeLenfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_ssizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t)
    @TruffleBoundary
    public static native long executeSsizeargfunc(long target, long ctx, long arg0, long arg1);

    // typedef HPy (*HPyFunc_ssizessizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy_ssize_t)
    @TruffleBoundary
    public static native long executeSsizessizeargfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_ssizeobjargproc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy)
    @TruffleBoundary
    public static native int executeSsizeobjargproc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_ssizessizeobjargproc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy_ssize_t, HPy)
    @TruffleBoundary
    public static native int executeSsizessizeobjargproc(long target, long ctx, long arg0, long arg1, long arg2, long arg3);

    // typedef int (*HPyFunc_objobjargproc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native int executeObjobjargproc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef void (*HPyFunc_freefunc)(HPyContext *ctx, void *)
    @TruffleBoundary
    public static native void executeFreefunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_getattrfunc)(HPyContext *ctx, HPy, char *)
    @TruffleBoundary
    public static native long executeGetattrfunc(long target, long ctx, long arg0, long arg1);

    // typedef HPy (*HPyFunc_getattrofunc)(HPyContext *ctx, HPy, HPy)
    @TruffleBoundary
    public static native long executeGetattrofunc(long target, long ctx, long arg0, long arg1);

    // typedef int (*HPyFunc_setattrfunc)(HPyContext *ctx, HPy, char *, HPy)
    @TruffleBoundary
    public static native int executeSetattrfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_setattrofunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native int executeSetattrofunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef HPy (*HPyFunc_reprfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeReprfunc(long target, long ctx, long arg0);

    // typedef HPy_hash_t (*HPyFunc_hashfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeHashfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_richcmpfunc)(HPyContext *ctx, HPy, HPy, HPy_RichCmpOp)
    @TruffleBoundary
    public static native long executeRichcmpfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef HPy (*HPyFunc_getiterfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeGetiterfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_iternextfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeIternextfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_descrgetfunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native long executeDescrgetfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_descrsetfunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native int executeDescrsetfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_initproc)(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs, HPy kw)
    @TruffleBoundary
    public static native int executeInitproc(long target, long ctx, long self, long args, long nargs, long kw);

    // typedef HPy (*HPyFunc_getter)(HPyContext *ctx, HPy, void *)
    @TruffleBoundary
    public static native long executeGetter(long target, long ctx, long arg0, long arg1);

    // typedef int (*HPyFunc_setter)(HPyContext *ctx, HPy, HPy, void *)
    @TruffleBoundary
    public static native int executeSetter(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_objobjproc)(HPyContext *ctx, HPy, HPy)
    @TruffleBoundary
    public static native int executeObjobjproc(long target, long ctx, long arg0, long arg1);

    // typedef int (*HPyFunc_getbufferproc)(HPyContext *ctx, HPy, HPy_buffer *, int)
    @TruffleBoundary
    public static native int executeGetbufferproc(long target, long ctx, long arg0, long arg1, int arg2);

    // typedef void (*HPyFunc_releasebufferproc)(HPyContext *ctx, HPy, HPy_buffer *)
    @TruffleBoundary
    public static native void executeReleasebufferproc(long target, long ctx, long arg0, long arg1);

    // typedef void (*HPyFunc_destructor)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native void executeDestructor(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_noargs)(HPyContext *ctx, HPy self)
    @TruffleBoundary
    public static native long executeDebugNoargs(long target, long ctx, long self);

    // typedef HPy (*HPyFunc_o)(HPyContext *ctx, HPy self, HPy arg)
    @TruffleBoundary
    public static native long executeDebugO(long target, long ctx, long self, long arg);

    // typedef HPy (*HPyFunc_varargs)(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs)
    @TruffleBoundary
    public static native long executeDebugVarargs(long target, long ctx, long self, long args, long nargs);

    // typedef HPy (*HPyFunc_keywords)(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs, HPy kw)
    @TruffleBoundary
    public static native long executeDebugKeywords(long target, long ctx, long self, long args, long nargs, long kw);

    // typedef HPy (*HPyFunc_unaryfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeDebugUnaryfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_binaryfunc)(HPyContext *ctx, HPy, HPy)
    @TruffleBoundary
    public static native long executeDebugBinaryfunc(long target, long ctx, long arg0, long arg1);

    // typedef HPy (*HPyFunc_ternaryfunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native long executeDebugTernaryfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_inquiry)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native int executeDebugInquiry(long target, long ctx, long arg0);

    // typedef HPy_ssize_t (*HPyFunc_lenfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeDebugLenfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_ssizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t)
    @TruffleBoundary
    public static native long executeDebugSsizeargfunc(long target, long ctx, long arg0, long arg1);

    // typedef HPy (*HPyFunc_ssizessizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy_ssize_t)
    @TruffleBoundary
    public static native long executeDebugSsizessizeargfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_ssizeobjargproc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy)
    @TruffleBoundary
    public static native int executeDebugSsizeobjargproc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_ssizessizeobjargproc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy_ssize_t, HPy)
    @TruffleBoundary
    public static native int executeDebugSsizessizeobjargproc(long target, long ctx, long arg0, long arg1, long arg2, long arg3);

    // typedef int (*HPyFunc_objobjargproc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native int executeDebugObjobjargproc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef void (*HPyFunc_freefunc)(HPyContext *ctx, void *)
    @TruffleBoundary
    public static native void executeDebugFreefunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_getattrfunc)(HPyContext *ctx, HPy, char *)
    @TruffleBoundary
    public static native long executeDebugGetattrfunc(long target, long ctx, long arg0, long arg1);

    // typedef HPy (*HPyFunc_getattrofunc)(HPyContext *ctx, HPy, HPy)
    @TruffleBoundary
    public static native long executeDebugGetattrofunc(long target, long ctx, long arg0, long arg1);

    // typedef int (*HPyFunc_setattrfunc)(HPyContext *ctx, HPy, char *, HPy)
    @TruffleBoundary
    public static native int executeDebugSetattrfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_setattrofunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native int executeDebugSetattrofunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef HPy (*HPyFunc_reprfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeDebugReprfunc(long target, long ctx, long arg0);

    // typedef HPy_hash_t (*HPyFunc_hashfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeDebugHashfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_richcmpfunc)(HPyContext *ctx, HPy, HPy, HPy_RichCmpOp)
    @TruffleBoundary
    public static native long executeDebugRichcmpfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef HPy (*HPyFunc_getiterfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeDebugGetiterfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_iternextfunc)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native long executeDebugIternextfunc(long target, long ctx, long arg0);

    // typedef HPy (*HPyFunc_descrgetfunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native long executeDebugDescrgetfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_descrsetfunc)(HPyContext *ctx, HPy, HPy, HPy)
    @TruffleBoundary
    public static native int executeDebugDescrsetfunc(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_initproc)(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs, HPy kw)
    @TruffleBoundary
    public static native int executeDebugInitproc(long target, long ctx, long self, long args, long nargs, long kw);

    // typedef HPy (*HPyFunc_getter)(HPyContext *ctx, HPy, void *)
    @TruffleBoundary
    public static native long executeDebugGetter(long target, long ctx, long arg0, long arg1);

    // typedef int (*HPyFunc_setter)(HPyContext *ctx, HPy, HPy, void *)
    @TruffleBoundary
    public static native int executeDebugSetter(long target, long ctx, long arg0, long arg1, long arg2);

    // typedef int (*HPyFunc_objobjproc)(HPyContext *ctx, HPy, HPy)
    @TruffleBoundary
    public static native int executeDebugObjobjproc(long target, long ctx, long arg0, long arg1);

    // typedef int (*HPyFunc_getbufferproc)(HPyContext *ctx, HPy, HPy_buffer *, int)
    @TruffleBoundary
    public static native int executeDebugGetbufferproc(long target, long ctx, long arg0, long arg1, int arg2);

    // typedef void (*HPyFunc_releasebufferproc)(HPyContext *ctx, HPy, HPy_buffer *)
    @TruffleBoundary
    public static native void executeDebugReleasebufferproc(long target, long ctx, long arg0, long arg1);

    // typedef void (*HPyFunc_destructor)(HPyContext *ctx, HPy)
    @TruffleBoundary
    public static native void executeDebugDestructor(long target, long ctx, long arg0);
    // {{end autogen}}
}
