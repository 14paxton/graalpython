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
package com.oracle.graal.python.builtins.objects.cext;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodesFactory.UnicodeAsWideCharNodeGen;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class UnicodeObjectNodes {

    abstract static class UnicodeBaseNode extends PBaseNode {
        private static final int NATIVE_ORDER = 0;
        private static Charset UTF32;
        private static Charset UTF32LE;
        private static Charset UTF32BE;

        protected static Charset getUTF32Charset(int byteorder) {
            String utf32Name = getUTF32Name(byteorder);
            if (byteorder == UnicodeBaseNode.NATIVE_ORDER) {
                if (UTF32 == null) {
                    UTF32 = Charset.forName(utf32Name);
                }
                return UTF32;
            } else if (byteorder < UnicodeBaseNode.NATIVE_ORDER) {
                if (UTF32LE == null) {
                    UTF32LE = Charset.forName(utf32Name);
                }
                return UTF32LE;
            }
            if (UTF32BE == null) {
                UTF32BE = Charset.forName(utf32Name);
            }
            return UTF32BE;
        }

        protected static String getUTF32Name(int byteorder) {
            String csName;
            if (byteorder == 0) {
                csName = "UTF-32";
            } else if (byteorder < 0) {
                csName = "UTF-32LE";
            } else {
                csName = "UTF-32BE";
            }
            return csName;
        }
    }

    public abstract static class UnicodeAsWideCharNode extends UnicodeBaseNode {

        private final int byteOrder;

        protected UnicodeAsWideCharNode(int byteOrder) {
            this.byteOrder = byteOrder;
        }

        public abstract PBytes execute(Object obj, long elementSize, long elements);

        @Specialization
        PBytes doUnicode(PString s, long elementSize, long elements) {
            return doUnicode(s.getValue(), elementSize, elements);
        }

        @Specialization
        @TruffleBoundary
        PBytes doUnicode(String s, long elementSize, long elements) {
            // use native byte order
            Charset utf32Charset = getUTF32Charset(-1);

            // elementSize == 2: Store String in 'wchar_t' of size == 2, i.e., use UCS2. This is
            // achieved by decoding to UTF32 (which is basically UCS4) and ignoring the two
            // MSBs.
            if (elementSize == 2L) {
                ByteBuffer bytes = ByteBuffer.wrap(s.getBytes(utf32Charset));
                // FIXME unsafe narrowing
                int size;
                if (elements >= 0) {
                    size = Math.min(bytes.remaining() / 2, (int) (elements * elementSize));
                } else {
                    size = bytes.remaining() / 2;
                }
                ByteBuffer buf = ByteBuffer.allocate(size);
                while (bytes.remaining() >= 4) {
                    if (byteOrder < UnicodeBaseNode.NATIVE_ORDER) {
                        buf.putChar((char) ((bytes.getInt() & 0xFFFF0000) >> 16));
                    } else {
                        buf.putChar((char) (bytes.getInt() & 0x0000FFFF));
                    }
                }
                buf.flip();
                byte[] barr = new byte[buf.remaining()];
                buf.get(barr);
                return factory().createBytes(barr);
            } else if (elementSize == 4L) {
                return factory().createBytes(s.getBytes(utf32Charset));
            } else {
                throw new RuntimeException("unsupported wchar size; was: " + elementSize);
            }
        }

        public static UnicodeAsWideCharNode create(int byteOrder) {
            return UnicodeAsWideCharNodeGen.create(byteOrder);
        }
    }

}
