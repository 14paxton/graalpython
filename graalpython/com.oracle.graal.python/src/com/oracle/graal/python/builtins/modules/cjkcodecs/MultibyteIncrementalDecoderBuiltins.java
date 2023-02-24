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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteIncrementalDecoder;
import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.MAXSIZE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.internalErrorCallback;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStatefulDecoderContext.MAXDECPENDING;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_TOOFEW;
import static com.oracle.graal.python.nodes.ErrorMessages.CODEC_IS_UNEXPECTED_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.PENDING_BUFFER_OVERFLOW;
import static com.oracle.graal.python.nodes.ErrorMessages.PENDING_BUFFER_TOO_LARGE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = MultibyteIncrementalDecoder)
public class MultibyteIncrementalDecoderBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultibyteStreamWriterBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "errors",})
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBinaryBuiltinNode {

        private static final TruffleString CODEC = tsLiteral("codec");

        @Specialization
        protected Object mbstreamreaderNew(VirtualFrame frame, Object type, Object err,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached TruffleString.EqualNode isEqual) { // "|s:IncrementalDecoder"
            TruffleString errors = null;
            if (err != PNone.NO_VALUE) {
                errors = castToStringNode.execute(err);
            }

            MultibyteIncrementalDecoderObject self = factory().createMultibyteIncrementalDecoderObject(type);

            Object codec = getAttr.execute(frame, type, CODEC);
            if (!(codec instanceof MultibyteCodecObject)) {
                throw raise(TypeError, CODEC_IS_UNEXPECTED_TYPE);
            }

            self.codec = ((MultibyteCodecObject) codec).codec;
            self.pendingsize = 0;
            self.errors = internalErrorCallback(errors, isEqual);
            self.state = self.codec.decinit(self.errors);
            return self;
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone init(@SuppressWarnings("unused") MultibyteIncrementalDecoderObject self) {
            return PNone.NONE;
        }
    }

    /**
     * Utility functions for stateful codec mechanism
     */

    @Builtin(name = "decode", minNumOfPositionalArgs = 1, parameterNames = {"$self", "input", "final"}, doc = "decode($self, /, input, final=False)\n--\n\n")
    @ArgumentClinic(name = "input", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteIncrementalDecoderBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        // _multibytecodec_MultibyteIncrementalDecoder_decode_impl
        @Specialization
        Object decode(VirtualFrame frame, MultibyteIncrementalDecoderObject self, byte[] input, int end,
                        @Cached MultibyteCodecUtil.DecodeErrorNode decodeErrorNode) {
            byte[] data = input;
            int size = input.length;

            int origpending = self.pendingsize;

            int wsize;
            byte[] wdata = data;
            if (self.pendingsize != 0) {
                if (size > MAXSIZE - self.pendingsize) {
                    throw raise(MemoryError);
                }
                wsize = size + self.pendingsize;
                wdata = new byte[wsize];
                PythonUtils.arraycopy(self.pending, 0, wdata, 0, self.pendingsize);
                PythonUtils.arraycopy(data, 0, wdata, self.pendingsize, size);
                self.pendingsize = 0;
            }

            MultibyteDecodeBuffer buf = new MultibyteDecodeBuffer(wdata);

            decoderFeedBuffer(frame, self, buf, decodeErrorNode, getRaiseNode());

            if (end != 0 && !buf.isFull()) {
                try {
                    decodeErrorNode.execute(frame, self.codec,
                                    buf, self.errors, MBERR_TOOFEW);
                } catch (PException e) {
                    /* recover the original pending buffer */
                    PythonUtils.arraycopy(wdata, 0, self.pending, 0, origpending);
                    self.pendingsize = origpending;
                    throw e;
                }
            }

            if (!buf.isFull()) { /* pending sequence still exists */
                decoderAppendPending(self, buf, getRaiseNode());
            }

            return buf.toTString();
        }

        static int decoderAppendPending(MultibyteStatefulDecoderContext ctx,
                        MultibyteDecodeBuffer buf,
                        PRaiseNode raiseNode) {
            int npendings = buf.remaining();
            if (npendings + ctx.pendingsize > MAXDECPENDING ||
                            npendings > MAXSIZE - ctx.pendingsize) {
                throw raiseNode.raise(UnicodeError, PENDING_BUFFER_OVERFLOW);
            }
            buf.getRemaining(ctx.pending, ctx.pendingsize, npendings);
            ctx.pendingsize += npendings;
            return 0;
        }

        static int decoderFeedBuffer(VirtualFrame frame, MultibyteStatefulDecoderContext ctx,
                        MultibyteDecodeBuffer buf,
                        MultibyteCodecUtil.DecodeErrorNode decodeErrorNode,
                        PRaiseNode raiseNode) {
            while (!buf.isFull()) {
                int r = ctx.codec.decode(ctx.state, buf, raiseNode);
                if (r == 0 || r == MBERR_TOOFEW) {
                    break;
                } else {
                    decodeErrorNode.execute(frame, ctx.codec, buf, ctx.errors, r);
                }
            }
            return 0;
        }

    }

    public static final HiddenKey DECODER_OBJECT_ATTR = new HiddenKey("decoder_object");

    @Builtin(name = "getstate", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "getstate($self, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class GetStateNode extends PythonUnaryBuiltinNode {

        // _multibytecodec_MultibyteIncrementalDecoder_getstate_impl
        Object getstate(MultibyteIncrementalDecoderObject self,
                        @Cached WriteAttributeToDynamicObjectNode writeAttrNode) {
            PBytes buffer = factory().createBytes(Arrays.copyOf(self.pending, self.pendingsize));
            PInt statelong = factory().createInt(0);
            writeAttrNode.execute(statelong, DECODER_OBJECT_ATTR, self.state);
            return factory().createTuple(new Object[]{buffer, statelong});
        }
    }

    @Builtin(name = "setstate", minNumOfPositionalArgs = 2, parameterNames = {"$self", "state"}, doc = "setstate($self, state, /)\n--\n\n")
    @ArgumentClinic(name = "state", conversion = ArgumentClinic.ClinicConversion.Tuple)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteIncrementalDecoderBuiltinsClinicProviders.SetStateNodeClinicProviderGen.INSTANCE;
        }

        // _multibytecodec_MultibyteIncrementalDecoder_setstate_impl
        Object setstate(VirtualFrame frame, MultibyteIncrementalDecoderObject self, PTuple state,
                        @Cached ReadAttributeFromDynamicObjectNode readAttrNode,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray) {
            Object[] array = getArray.execute(state.getSequenceStorage());
            Object buffer = array[0];
            Object statelong = array[1];

            byte[] bufferstr = toBytesNode.execute(frame, buffer);
            int buffersize = bufferstr.length;
            if (buffersize > MAXDECPENDING) {
                throw raise(UnicodeError, PENDING_BUFFER_TOO_LARGE);
            }

            self.pendingsize = buffersize;
            PythonUtils.arraycopy(bufferstr, 0, self.pending, 0, self.pendingsize);
            Object s = readAttrNode.execute(statelong, DECODER_OBJECT_ATTR);
            if (s == PNone.NO_VALUE) {
                self.state = null;
            } else {
                assert s instanceof MultibyteCodecState : "Not MultibyteCodecState object!";
                self.state = (MultibyteCodecState) s;
            }
            // PythonUtils.arraycopy(statebytes, 0, self.state.c, 0, MULTIBYTECODECSTATE);

            return PNone.NONE;
        }
    }

    @Builtin(name = "reset", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "reset($self, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class ResetNode extends PythonUnaryBuiltinNode {

        // _multibytecodec_MultibyteIncrementalDecoder_reset_impl
        @Specialization
        static Object reset(MultibyteIncrementalDecoderObject self) {
            self.codec.decreset(self.state);
            self.pendingsize = 0;
            return PNone.NONE;
        }
    }

}
