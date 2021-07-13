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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BinasciiError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@CoreFunctions(defineModule = "binascii")
public class BinasciiModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BinasciiModuleBuiltinsFactory.getFactories();
    }

    abstract static class AsciiBufferConverter extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        @Specialization(guards = "acquireLib.hasBuffer(value)", limit = "getCallSiteInlineCacheMaxDepth()")
        Object doObject(Object value,
                        @CachedLibrary("value") PythonBufferAcquireLibrary acquireLib) {
            return acquireLib.acquireReadonly(value);
        }

        @ExportLibrary(PythonBufferAccessLibrary.class)
        static final class AsciiStringBuffer {
            private final String str;

            AsciiStringBuffer(String str) {
                this.str = str;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isBuffer() {
                return true;
            }

            @ExportMessage
            int getBufferLength() {
                return str.length();
            }

            @ExportMessage
            @TruffleBoundary
            byte readByte(int byteOffset,
                            @Cached PRaiseNode raise) {
                // TODO make this efficient when we get TruffleStrings
                char ch = str.charAt(byteOffset);
                if (ch >= 128) {
                    throw raise.raise(ValueError, "string argument should contain only ASCII characters");
                }
                return (byte) ch;
            }
        }

        @Specialization
        Object string(String value) {
            return new AsciiStringBuffer(value);
        }

        @Specialization
        Object string(PString value,
                        @Cached CastToJavaStringNode cast) {
            return string(cast.execute(value));
        }

        @Fallback
        Object error(@SuppressWarnings("unused") Object value) {
            throw raise(TypeError, "argument should be bytes, buffer or ASCII string, not '%p'", value);
        }

        @ClinicConverterFactory
        public static AsciiBufferConverter create() {
            return BinasciiModuleBuiltinsFactory.AsciiBufferConverterNodeGen.create();
        }
    }

    @Builtin(name = "a2b_base64", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data"})
    @ArgumentClinic(name = "data", conversionClass = AsciiBufferConverter.class)
    @GenerateNodeFactory
    abstract static class A2bBase64Node extends PythonUnaryClinicBuiltinNode {
        @Specialization(limit = "3")
        PBytes doConvert(Object buffer,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                return b64decode(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer));
            } finally {
                bufferLib.release(buffer);
            }
        }

        @TruffleBoundary
        private PBytes b64decode(byte[] data, int dataLen) {
            try {
                // Using MIME decoder because that one skips over anything that is not the alphabet,
                // just like CPython does
                ByteBuffer result = Base64.getMimeDecoder().decode(ByteBuffer.wrap(data, 0, dataLen));
                return factory().createBytes(result.array(), result.limit());
            } catch (IllegalArgumentException e) {
                throw raise(BinasciiError, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.A2bBase64NodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "a2b_hex", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data"})
    @ArgumentClinic(name = "data", conversionClass = AsciiBufferConverter.class)
    @GenerateNodeFactory
    abstract static class A2bHexNode extends PythonUnaryClinicBuiltinNode {
        @Specialization(limit = "3")
        PBytes a2b(Object buffer,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                return a2b(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer));
            } finally {
                bufferLib.release(buffer);
            }
        }

        @TruffleBoundary
        private PBytes a2b(byte[] bytes, int length) {
            if (length % 2 != 0) {
                throw raise(BinasciiError, ErrorMessages.ODD_LENGTH_STRING);
            }
            byte[] output = new byte[length / 2];
            for (int i = 0; i < length / 2; i++) {
                output[i] = (byte) (digitValue((char) bytes[i * 2]) * 16 + digitValue((char) bytes[i * 2 + 1]));
            }
            return factory().createBytes(output);
        }

        private int digitValue(char b) {
            if (b >= '0' && b <= '9') {
                return b - '0';
            } else if (b >= 'a' && b <= 'f') {
                return b - 'a' + 10;
            } else if (b >= 'A' && b <= 'F') {
                return b - 'A' + 10;
            } else {
                throw raise(BinasciiError, ErrorMessages.NON_HEX_DIGIT_FOUND);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.A2bHexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "b2a_base64", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data"}, keywordOnlyNames = {"newline"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class B2aBase64Node extends PythonClinicBuiltinNode {
        @TruffleBoundary
        private PBytes b2a(byte[] data, int lenght, int newline) {
            ByteBuffer encoded;
            try {
                encoded = Base64.getEncoder().encode(ByteBuffer.wrap(data, 0, lenght));
            } catch (IllegalArgumentException e) {
                throw raise(BinasciiError, e);
            }
            if (newline != 0) {
                byte[] encodedWithNL = Arrays.copyOf(encoded.array(), encoded.limit() + 1);
                encodedWithNL[encodedWithNL.length - 1] = '\n';
                return factory().createBytes(encodedWithNL);
            }
            return factory().createBytes(encoded.array(), encoded.limit());
        }

        @Specialization(limit = "3")
        PBytes b2aBuffer(Object buffer, int newline,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                return b2a(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer), newline);
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.B2aBase64NodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "b2a_hex", minNumOfPositionalArgs = 1, parameterNames = {"data", "sep", "bytes_per_sep"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "bytes_per_sep", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class B2aHexNode extends PythonTernaryClinicBuiltinNode {

        @CompilationFinal(dimensions = 1) private static final byte[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        @Specialization(limit = "3")
        PBytes b2a(Object buffer, Object sep, int bytesPerSep,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            if (sep != PNone.NO_VALUE || bytesPerSep != 1) {
                // TODO implement sep and bytes_per_sep
                throw raise(NotImplementedError);
            }
            try {
                return b2a(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer));
            } finally {
                bufferLib.release(buffer);
            }
        }

        @TruffleBoundary
        private PBytes b2a(byte[] bytes, int length) {
            byte[] output = new byte[length * 2];
            for (int i = 0; i < length; i++) {
                int v = bytes[i] & 0xff;
                output[i * 2] = HEX_DIGITS[v >> 4];
                output[i * 2 + 1] = HEX_DIGITS[v & 0xf];
            }
            return factory().createBytes(output);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.B2aHexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "crc32", minNumOfPositionalArgs = 1, parameterNames = {"data", "crc"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    // TODO crc argument
    @GenerateNodeFactory
    abstract static class Crc32Node extends PythonBinaryClinicBuiltinNode {
        // TODO crc != NO_VALUE
        @Specialization(guards = "isNoValue(crc)", limit = "3")
        static long b2a(Object buffer, @SuppressWarnings("unused") PNone crc,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                return getCrcValue(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer));
            } finally {
                bufferLib.release(buffer);
            }
        }

        @TruffleBoundary
        private static long getCrcValue(byte[] bytes, int length) {
            CRC32 crc32 = new CRC32();
            crc32.update(bytes, 0, length);
            return crc32.getValue();
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.Crc32NodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "hexlify", minNumOfPositionalArgs = 1, parameterNames = {"data", "sep", "bytes_per_sep"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "bytes_per_sep", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class HexlifyNode extends B2aHexNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.HexlifyNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "unhexlify", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data"})
    @ArgumentClinic(name = "data", conversionClass = AsciiBufferConverter.class)
    @GenerateNodeFactory
    abstract static class UnhexlifyNode extends A2bHexNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.UnhexlifyNodeClinicProviderGen.INSTANCE;
        }
    }
}
