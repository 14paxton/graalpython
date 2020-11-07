/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.runtime.NativeLibrary.InvokeNativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.NFIBackend;
import com.oracle.graal.python.runtime.NativeLibrary.NativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.TypedNativeLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport extends PosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "libposix";

    private static final int UNAME_BUF_LENGTH = 256;

    enum NativeFunctions implements NativeFunction {
        get_errno("():sint32"),
        set_errno("(sint32):void"),
        call_strerror("(sint32, [sint8], sint32):void"),
        call_getpid("():sint64"),
        call_umask("(sint64):sint64"),
        call_openat("(sint32, [sint8], sint32, sint32):sint32"),
        call_close("(sint32):sint32"),
        call_read("(sint32, [sint8], uint64):sint64"),
        call_write("(sint32, [sint8], uint64):sint64"),
        call_dup("(sint32):sint32"),
        call_dup2("(sint32, sint32, sint32):sint32"),
        call_pipe2("([sint32]):sint32"),
        call_lseek("(sint32, sint64, sint32):sint64"),
        call_ftruncate("(sint32, sint64):sint32"),
        call_fsync("(sint32):sint32"),
        call_fstatat("(sint32, [sint8], sint32, [sint64]):sint32"),
        call_fstat("(sint32, [sint64]):sint32"),
        call_uname("([sint8], [sint8], [sint8], [sint8], [sint8], sint32):sint32"),
        call_unlinkat("(sint32, [sint8], sint32):sint32"),
        call_symlinkat("([sint8], sint32, [sint8]):sint32"),
        call_mkdirat("(sint32, [sint8], sint32):sint32"),
        call_getcwd("([sint8], uint64):sint32"),
        call_chdir("([sint8]):sint32"),
        call_fchdir("(sint32):sint32"),
        call_isatty("(sint32):sint32"),
        get_inheritable("(sint32):sint32"),
        set_inheritable("(sint32, sint32):sint32"),
        get_blocking("(sint32):sint32"),
        set_blocking("(sint32, sint32):sint32"),
        get_terminal_size("(sint32, [sint32]):sint32");

        private final String signature;

        NativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private final PythonContext context;
    private final TypedNativeLibrary<NativeFunctions> lib;

    private NFIPosixSupport(PythonContext context, NFIBackend backend) {
        this.context = context;
        lib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME, NativeFunctions.values(), backend,
                        "You can switch to pure Java emulated POSIX support, " +
                                        "which does not require native access privilege, by passing option " +
                                        "'--python.PosixModuleBackend=java'.");
    }

    public static NFIPosixSupport createNative(PythonContext context) {
        return new NFIPosixSupport(context, NFIBackend.NATIVE);
    }

    public static NFIPosixSupport createLLVM(PythonContext context) {
        return new NFIPosixSupport(context, NFIBackend.LLVM);
    }

    @ExportMessage
    public String getBackend() {
        return lib.getNfiBackend() == NFIBackend.LLVM ? "llvm" : "native";
    }

    @ExportMessage
    public String strerror(int errorCode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        // From man pages: The GNU C Library uses a buffer of 1024 characters for strerror().
        // This buffer size therefore should be sufficient to avoid an ERANGE error when calling
        // strerror_r().
        byte[] buf = new byte[1024];
        invokeNode.call(lib, NativeFunctions.call_strerror, errorCode, wrap(buf), buf.length);
        // TODO PyUnicode_DecodeLocale
        return cStringToJavaString(buf);
    }

    @ExportMessage
    public long getpid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(lib, NativeFunctions.call_getpid);
    }

    @ExportMessage
    public long umask(long mask,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        long result = invokeNode.callLong(lib, NativeFunctions.call_umask, mask);
        if (result < 0) {
            // TODO call errno() and raise OS error
            // create helper method for this (like CPython's posix_error)
        }
        return result;
    }

    @ExportMessage
    public int openAt(int dirFd, PosixPath pathname, int flags, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            int fd = invokeNode.callInt(lib, NativeFunctions.call_openat, dirFd, pathToCString(pathname), flags, mode);
            if (fd >= 0) {
                return fd;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno, pathname.originalObject);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public void close(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(lib, NativeFunctions.call_close, fd) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public Buffer read(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        Buffer buffer = Buffer.allocate(length);
        while (true) {
            setErrno(invokeNode, 0);
            long n = invokeNode.callLong(lib, NativeFunctions.call_read, fd, wrap(buffer), length);
            if (n >= 0) {
                return buffer.withLength(n);
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public long write(int fd, Buffer data,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            setErrno(invokeNode, 0);
            long n = invokeNode.callLong(lib, NativeFunctions.call_write, fd, wrap(data), data.length);
            if (n >= 0) {
                return n;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public int dup(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int newFd = invokeNode.callInt(lib, NativeFunctions.call_dup, fd);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return newFd;
    }

    @ExportMessage
    public int dup2(int fd, int fd2, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int newFd = invokeNode.callInt(lib, NativeFunctions.call_dup2, fd, fd2, inheritable ? 1 : 0);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return newFd;
    }

    @ExportMessage
    public boolean getInheritable(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.get_inheritable, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result != 0;
    }

    @ExportMessage
    public void setInheritable(int fd, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(lib, NativeFunctions.set_inheritable, fd, inheritable ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] pipe(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] fds = new int[2];
        if (invokeNode.callInt(lib, NativeFunctions.call_pipe2, context.getEnv().asGuestValue(fds)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return fds;
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long res = invokeNode.callLong(lib, NativeFunctions.call_lseek, fd, offset, how);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public void ftruncate(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_ftruncate, fd, length);
            if (res == 0) {
                return;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public void fsync(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_fsync, fd);
            if (res == 0) {
                return;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public boolean getBlocking(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.get_blocking, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result != 0;
    }

    @ExportMessage
    public void setBlocking(int fd, boolean blocking,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(lib, NativeFunctions.set_blocking, fd, blocking ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] getTerminalSize(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] size = new int[2];
        if (invokeNode.callInt(lib, NativeFunctions.get_terminal_size, fd, context.getEnv().asGuestValue(size)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return size;
    }

    @ExportMessage
    public long[] fstatAt(int dirFd, PosixPath pathname, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[13];
        int res = invokeNode.callInt(lib, NativeFunctions.call_fstatat, dirFd, pathToCString(pathname), followSymlinks ? 1 : 0, context.getEnv().asGuestValue(out));
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
        return out;
    }

    @ExportMessage
    public long[] fstat(int fd, Object filename, boolean handleEintr,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        long[] out = new long[13];
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_fstat, fd, context.getEnv().asGuestValue(out));
            if (res == 0) {
                return out;
            }
            int errno = getErrno(invokeNode);
            if (!handleEintr || errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno, filename);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public Object[] uname(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        byte[] sys = new byte[UNAME_BUF_LENGTH];
        byte[] node = new byte[UNAME_BUF_LENGTH];
        byte[] rel = new byte[UNAME_BUF_LENGTH];
        byte[] ver = new byte[UNAME_BUF_LENGTH];
        byte[] machine = new byte[UNAME_BUF_LENGTH];
        int res = invokeNode.callInt(lib, NativeFunctions.call_uname, wrap(sys), wrap(node), wrap(rel), wrap(ver), wrap(machine), UNAME_BUF_LENGTH);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new Object[]{
                        // TODO PyUnicode_DecodeFSDefault
                        cStringToJavaString(sys),
                        cStringToJavaString(node),
                        cStringToJavaString(rel),
                        cStringToJavaString(ver),
                        cStringToJavaString(machine)
        };
    }

    @ExportMessage
    public void unlinkAt(int dirFd, PosixPath pathname, boolean rmdir,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_unlinkat, dirFd, pathToCString(pathname), rmdir ? 1 : 0);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
    }

    @ExportMessage
    public void symlinkAt(PosixPath target, int linkpathDirFd, PosixPath linkpath,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_symlinkat, pathToCString(target), linkpathDirFd, pathToCString(linkpath));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), target.originalObject, linkpath.originalObject);
        }
    }

    @ExportMessage
    public void mkdirAt(int dirFd, PosixPath pathname, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_mkdirat, dirFd, pathToCString(pathname), mode);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
    }

    @ExportMessage
    public Buffer getcwdb(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        for (int bufLen = 1024;; bufLen += 1024) {
            Buffer buffer = Buffer.allocate(bufLen);
            int n = invokeNode.callInt(lib, NativeFunctions.call_getcwd, wrap(buffer), bufLen);
            if (n == 0) {
                return buffer.withLength(findZero(buffer.data));
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.ERANGE.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
        }
    }

    @ExportMessage
    public String getcwd(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        Buffer result = getcwdb(invokeNode);
        if (result.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix getcwd() returned more than can fit to a Java array");
        }
        // TODO PyUnicode_DecodeFSDefault
        return PythonUtils.newString(result.data, 0, (int) result.length);
    }

    @ExportMessage
    public void chdir(PosixPath path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_chdir, pathToCString(path));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), path.originalObject);
        }
    }

    @ExportMessage
    public void fchdir(int fd, Object filename, boolean handleEintr,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            if (invokeNode.callInt(lib, NativeFunctions.call_fchdir, fd) == 0) {
                return;
            }
            int errno = getErrno(invokeNode);
            if (!handleEintr || errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno, filename);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public boolean isatty(int fd,
                       @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(lib, NativeFunctions.call_isatty, fd) != 0;
    }

    private int getErrno(InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(lib, NativeFunctions.get_errno);
    }

    private void setErrno(InvokeNativeFunction invokeNode, int errno) {
        invokeNode.call(lib, NativeFunctions.set_errno, errno);
    }

    private PosixException getErrnoAndThrowPosixException(InvokeNativeFunction invokeNode) throws PosixException {
        throw newPosixException(invokeNode, getErrno(invokeNode));
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno) throws PosixException {
        throw new PosixException(errno, strerror(errno, invokeNode));
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno, Object filename) throws PosixException {
        throw new PosixException(errno, strerror(errno, invokeNode), filename);
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno, Object filename1, Object filename2) throws PosixException {
        throw new PosixException(errno, strerror(errno, invokeNode), filename1, filename2);
    }

    private Object wrap(byte[] bytes) {
        return context.getEnv().asGuestValue(bytes);
    }

    private Object wrap(Buffer buffer) {
        return context.getEnv().asGuestValue(buffer.data);
    }

    private static String cStringToJavaString(byte[] buf) {
        return PythonUtils.newString(buf, 0, findZero(buf));
    }

    private static int findZero(byte[] buf) {
        for (int i = 0; i < buf.length; ++i) {
            if (buf[i] == 0) {
                return i;
            }
        }
        return buf.length;
    }

    private Object pathToCString(PosixPath path) {
        return wrap(nullTerminate(path.path));
    }

    private static byte[] nullTerminate(byte[] str) {
        byte[] terminated = new byte[str.length + 1];
        PythonUtils.arraycopy(str, 0, terminated, 0, str.length);
        return terminated;
    }
}
