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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.LookupError;
import static com.oracle.graal.python.nodes.BuiltinNames.ENCODE;
import static com.oracle.graal.python.nodes.BuiltinNames._CODECS_TRUFFLE;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_TEXT_ENCODING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsDecodeNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsEncodeNode;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.CallApplyNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.CodecDecodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.CodecInitNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.EncodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.IncrementalDecodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.IncrementalEncodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.StreamDecodeNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSuperClassNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = _CODECS_TRUFFLE)
public class CodecsTruffleModuleBuiltins extends PythonBuiltins {

    private static final String CODEC_INFO_NAME = "CodecInfo";
    private static final String CODEC = "Codec";
    private static final String INCREMENTAL_ENCODER = "IncrementalEncoder";
    private static final String BUFFERED_INCREMENTAL_DECODER = "BufferedIncrementalDecoder";
    private static final String STREAM_READER = "StreamReader";
    private static final String STREAM_WRITER = "StreamWriter";

    private static final String BUFFER_DECODE = "_buffer_decode";

    private static final String TRUFFLE_CODEC = "TruffleCodec";
    private static final String TRUFFLE_INCREMENTAL_ENCODER = "TruffleIncrementalEncoder";
    private static final String TRUFFLE_INCREMENTAL_DECODER = "TruffleIncrementalDecoder";
    private static final String TRUFFLE_STREAM_WRITER = "TruffleStreamWriter";
    private static final String TRUFFLE_STREAM_READER = "TruffleStreamReader";
    private static final String APPLY_ENCODING = "ApplyEncoding";

    private static final String ATTR_ENCODING = "encoding";
    private static final String ATTR_ERRORS = "errors";
    private static final String ATTR_FN = "fn";

    private PythonClass truffleCodecClass;
    private PythonClass truffleIncrementalEncoderClass;
    private PythonClass truffleIncrementalDecoderClass;
    private PythonClass truffleStreamReaderClass;
    private PythonClass truffleStreamWriterClass;
    private PythonClass applyEncodingClass;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsTruffleModuleBuiltinsFactory.getFactories();
    }

    private static PythonClass initClass(String className, String superClassName, BuiltinDescr[] descrs, PythonModule codecsTruffleModule, PythonModule codecsModule, PythonLanguage language,
                    PythonObjectFactory factory) {
        PythonAbstractClass superClass = (PythonAbstractClass) codecsModule.getAttribute(superClassName);
        return initClass(className, superClass, descrs, codecsTruffleModule, language, factory);
    }

    private static PythonClass initClass(String className, PythonAbstractClass superClass, BuiltinDescr[] descrs, PythonModule codecsTruffleModule, PythonLanguage language,
                    PythonObjectFactory factory) {
        PythonClass clazz = factory.createPythonClassAndFixupSlots(language, PythonBuiltinClassType.PythonClass, className, new PythonAbstractClass[]{superClass});
        for (BuiltinDescr d : descrs) {
            PythonUtils.createMethod(language, clazz, d.nodeFactory.getNodeClass(), d.enclosingType ? clazz : null, 1, () -> d.nodeFactory.createNode(), factory);
        }
        clazz.setAttribute(__MODULE__, _CODECS_TRUFFLE);
        clazz.setAttribute(__QUALNAME__, _CODECS_TRUFFLE);
        codecsTruffleModule.setAttribute(className, clazz);
        return clazz;
    }

    private static final class BuiltinDescr {
        final NodeFactory<? extends PythonBuiltinBaseNode> nodeFactory;
        final boolean enclosingType;

        public BuiltinDescr(NodeFactory<? extends PythonBuiltinBaseNode> nodeFactory, boolean enclosingType) {
            this.nodeFactory = nodeFactory;
            this.enclosingType = enclosingType;
        }
    }

    @TruffleBoundary
    static PTuple codecsInfo(PythonModule self, String encoding, PythonContext context, PythonObjectFactory factory) {
        PythonModule codecsModule = (PythonModule) AbstractImportNode.importModule("codecs");
        CodecsTruffleModuleBuiltins codecsTruffleBuiltins = (CodecsTruffleModuleBuiltins) self.getBuiltins();
        if (self.getAttribute(TRUFFLE_CODEC) instanceof PNone) {
            initCodecClasses(self, codecsModule, context, factory);
        }

        // encode/decode methods for codecs.CodecInfo
        PythonObject truffleCodec = factory.createPythonObject(codecsTruffleBuiltins.truffleCodecClass);
        truffleCodec.setAttribute(ATTR_ENCODING, encoding);
        Object encodeMethod = PyObjectGetAttr.getUncached().execute(null, truffleCodec, ENCODE);
        Object decodeMethod = PyObjectGetAttr.getUncached().execute(null, truffleCodec, DECODE);

        // incrementalencoder factory function for codecs.CodecInfo
        PythonObject tie = factory.createPythonObject(codecsTruffleBuiltins.applyEncodingClass);
        tie.setAttribute(ATTR_FN, codecsTruffleBuiltins.truffleIncrementalEncoderClass);
        tie.setAttribute(ATTR_ENCODING, encoding);

        // incrementaldecoder factory function for codecs.CodecInfo
        PythonObject tid = factory.createPythonObject(codecsTruffleBuiltins.applyEncodingClass);
        tid.setAttribute(ATTR_FN, codecsTruffleBuiltins.truffleIncrementalDecoderClass);
        tid.setAttribute(ATTR_ENCODING, encoding);

        // streamwriter factory function for codecs.CodecInfo
        PythonObject sr = factory.createPythonObject(codecsTruffleBuiltins.applyEncodingClass);
        sr.setAttribute(ATTR_FN, codecsTruffleBuiltins.truffleStreamReaderClass);
        sr.setAttribute(ATTR_ENCODING, encoding);

        // streamreader factory function for codecs.CodecInfo
        PythonObject sw = factory.createPythonObject(codecsTruffleBuiltins.applyEncodingClass);
        sw.setAttribute(ATTR_FN, codecsTruffleBuiltins.truffleStreamWriterClass);
        sw.setAttribute(ATTR_ENCODING, encoding);

        // codecs.CodecInfo
        PythonAbstractClass codecInfoClass = (PythonAbstractClass) codecsModule.getAttribute(CODEC_INFO_NAME);
        return (PTuple) CallVarargsMethodNode.getUncached().execute(null, codecInfoClass, new Object[]{}, createCodecInfoArgs(encoding, encodeMethod, decodeMethod, tie, tid, sr, sw));
    }

    private static PKeyword[] createCodecInfoArgs(String encoding, Object encodeMethod, Object decodeMethod, PythonObject tie, PythonObject tid, PythonObject sr, PythonObject sw) {
        return new PKeyword[]{
                        new PKeyword("name", encoding),
                        new PKeyword("encode", encodeMethod),
                        new PKeyword("decode", decodeMethod),
                        new PKeyword("incrementalencoder", tie),
                        new PKeyword("incrementaldecoder", tid),
                        new PKeyword("streamreader", sr),
                        new PKeyword("streamwriter", sw)
        };
    }

    protected SetAttributeNode createSetFn() {
        return SetAttributeNode.create("fn");
    }

    protected SetAttributeNode createSetEncoding() {
        return SetAttributeNode.create(ATTR_ENCODING);
    }

    /**
     * create classes based on types declared in lib/3/codes.py
     */
    // @formatter:off
    private static void initCodecClasses(PythonModule codecsTruffleModule, PythonModule codecsModule, PythonContext context, PythonObjectFactory factory) {

        // TODO - the incremental codec and reader/writer won't work well with stateful
        // encodings, like some of the CJK encodings
        CodecsTruffleModuleBuiltins codecsTruffleBuiltins = (CodecsTruffleModuleBuiltins) codecsTruffleModule.getBuiltins();
        PythonLanguage language = PythonLanguage.get(null);

        // class TruffleCodec(codecs.Codec):
        //     def encode(self, input, errors='strict'):
        //         return _codecs.__truffle_encode__(input, self.encoding, errors)
        //     def decode(self, input, errors='strict'):
        //         return _codecs.__truffle_decode__(input, self.encoding, errors, True)
        codecsTruffleBuiltins.truffleCodecClass = initClass(TRUFFLE_CODEC, (PythonClass) codecsModule.getAttribute(CODEC),
                        new BuiltinDescr[]{
                                        new BuiltinDescr(EncodeNodeFactory.getInstance(), false),
                                        new BuiltinDescr(CodecDecodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, language, factory);

        // class TruffleIncrementalEncoder(codecs.IncrementalEncoder):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def encode(self, input, final=False):
        //         return _codecs.__truffle_encode__(input, self.encoding, self.errors)[0]
        codecsTruffleBuiltins.truffleIncrementalEncoderClass = initClass(TRUFFLE_INCREMENTAL_ENCODER, INCREMENTAL_ENCODER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(IncrementalEncodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language, factory);

        // class TruffleIncrementalDecoder(codecs.BufferedIncrementalDecoder):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def _buffer_decode(self, input, errors, final):
        //         return _codecs.__truffle_decode__(input, self.encoding, errors, final)
        codecsTruffleBuiltins.truffleIncrementalDecoderClass = initClass(TRUFFLE_INCREMENTAL_DECODER, BUFFERED_INCREMENTAL_DECODER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(IncrementalDecodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language, factory);

        // class TruffleStreamWriter(codecs.StreamWriter):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def encode(self, input, errors='strict'):
        //         return _codecs.__truffle_encode__(input, self.encoding, errors)            
        codecsTruffleBuiltins.truffleStreamWriterClass = initClass(TRUFFLE_STREAM_WRITER, STREAM_WRITER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(EncodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language, factory);

        // class TruffleStreamReader(codecs.StreamReader):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def decode(self, input, errors='strict'):
        //         return _codecs.__truffle_decode__(input, self.encoding, errors)
        codecsTruffleBuiltins.truffleStreamReaderClass = initClass(TRUFFLE_STREAM_READER, STREAM_READER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(StreamDecodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language, factory);

        // serves as factory function for CodecInfo-s incrementalencoder/decode and streamwriter/reader
        // class apply_encoding:
        //     def __call__(self, *args, **kwargs):
        //         return self.fn(self.encoding, *args, **kwargs)
        codecsTruffleBuiltins.applyEncodingClass = initClass(APPLY_ENCODING, context.getCore().lookupType(PythonBuiltinClassType.PythonObject),
                        new BuiltinDescr[]{new BuiltinDescr(CallApplyNodeFactory.getInstance(), false)},
                        codecsTruffleModule, language, factory);
    }
    // @formatter:on

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class CodecInitNode extends PythonVarargsBuiltinNode {
        @Specialization
        Object init(VirtualFrame frame, PythonObject self, Object[] args, PKeyword[] kw,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached("createSetAttr()") SetAttributeNode setAttrNode,
                        @Cached GetClassNode getClass,
                        @Cached GetSuperClassNode getSuperClassNode,
                        @Cached CallNode callNode) {
            assert args.length > 0;
            Object superClass = getSuperClassNode.execute(getClass.execute(self));
            Object superInit = getAttrNode.execute(frame, superClass, __INIT__);
            Object[] callArgs = new Object[args.length];
            callArgs[0] = self;
            if (args.length > 1) {
                PythonUtils.arraycopy(args, 1, callArgs, 1, args.length - 1);
            }
            callNode.execute(frame, superInit, callArgs, kw);
            setAttrNode.executeVoid(frame, self, args[0]);
            return PNone.NONE;
        }

        protected LookupAndCallVarargsNode createCallNode() {
            return LookupAndCallVarargsNode.create(__INIT__);
        }

        protected SetAttributeNode createSetAttr() {
            return SetAttributeNode.create(ATTR_ENCODING);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class CallApplyNode extends PythonVarargsBuiltinNode {
        @Specialization
        Object call(VirtualFrame frame, PythonObject self, Object[] args, PKeyword[] kw,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallVarargsMethodNode callNode) {
            Object[] callArgs = new Object[args.length + 1];
            callArgs[0] = getAttrNode.execute(frame, self, ATTR_ENCODING);
            PythonUtils.arraycopy(args, 0, callArgs, 1, args.length);
            return callNode.execute(frame, getAttrNode.execute(frame, self, ATTR_FN), callArgs, kw);
        }
    }

    @Builtin(name = ENCODE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    protected abstract static class EncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, PythonObject self, Object input, Object errors,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsEncodeNode encode) {
            return encode.call(frame, input, getAttrNode.execute(frame, self, ATTR_ENCODING), errors);
        }
    }

    @Builtin(name = DECODE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    protected abstract static class CodecDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, PythonObject self, Object input, Object errors,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsDecodeNode decode) {
            return decode.call(frame, input, getAttrNode.execute(frame, self, ATTR_ENCODING), errors, true);
        }
    }

    @Builtin(name = ENCODE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    protected abstract static class IncrementalEncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, PythonObject self, Object input, @SuppressWarnings("unused") Object ffinal,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsEncodeNode encode,
                        @Cached TupleBuiltins.GetItemNode getItemNode) {
            PTuple result = (PTuple) encode.call(frame, input, getAttrNode.execute(frame, self, ATTR_ENCODING), getAttrNode.execute(frame, self, ATTR_ERRORS));
            return getItemNode.execute(frame, result, 0);
        }
    }

    @Builtin(name = BUFFER_DECODE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    protected abstract static class IncrementalDecodeNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, PythonObject self, Object input, Object errors, Object ffinal,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsDecodeNode decode) {
            return decode.call(frame, input, getAttrNode.execute(frame, self, ATTR_ENCODING), errors, ffinal);
        }
    }

    @Builtin(name = DECODE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    protected abstract static class StreamDecodeNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, PythonObject self, Object input, Object errors, Object ffinal,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsDecodeNode decode) {
            return decode.call(frame, input, getAttrNode.execute(frame, self, ATTR_ENCODING), errors, ffinal);
        }
    }

    public abstract static class LookupTextEncoding extends PNodeWithRaise {
        public abstract Object execute(VirtualFrame frame, String encoding, String alternateCommand);

        @Specialization
        Object lookup(VirtualFrame frame, String encoding, String alternateCommand,
                        @Cached CodecsModuleBuiltins.LookupNode lookupNode,
                        @Cached("createGetAttributeNode()") GetAttributeNode getAttributeNode) {
            Object codecInfo = lookupNode.call(frame, encoding);
            Object isTextObj = getAttributeNode.executeObject(frame, codecInfo);
            if (!(codecInfo instanceof PTuple) || !((isTextObj instanceof Boolean) && (boolean) isTextObj)) {
                throw raise(LookupError, IS_NOT_TEXT_ENCODING, encoding, alternateCommand);
            }
            return codecInfo;
        }

        protected GetAttributeNode createGetAttributeNode() {
            return GetAttributeNode.create("_is_text_encoding");
        }
    }

    public abstract static class GetPreferredEncoding extends PNodeWithRaise {
        public abstract String execute(VirtualFrame frame);

        @Specialization
        String getpreferredencoding(VirtualFrame frame,
                        @Cached PyObjectCallMethodObjArgs callMethodNode,
                        @Cached PyObjectStrAsJavaStringNode strNode) {

            Object locale = AbstractImportNode.importModule("locale");
            Object e = callMethodNode.execute(frame, locale, "getpreferredencoding");
            return strNode.execute(frame, e);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class MakeIncrementalcodecNode extends PNodeWithRaise {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, Object errors, String attrName);

        @Specialization
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, @SuppressWarnings("unused") PNone errors, String attrName,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, codecInfo, attrName);
        }

        @Specialization(guards = "!isPNone(errors)")
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, Object errors, String attrName,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, codecInfo, attrName, errors);
        }
    }

    public abstract static class GetIncrementalEncoderNode extends PNodeWithRaise {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, String errors);

        @Specialization
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, String errors,
                        @Cached MakeIncrementalcodecNode makeIncrementalcodecNode) {
            return makeIncrementalcodecNode.execute(frame, codecInfo, errors, "incrementalencoder");
        }
    }

    public abstract static class GetIncrementalDecoderNode extends PNodeWithRaise {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, String errors);

        @Specialization
        Object getIncEncoder(VirtualFrame frame, Object codecInfo, String errors,
                        @Cached MakeIncrementalcodecNode makeIncrementalcodecNode) {
            return makeIncrementalcodecNode.execute(frame, codecInfo, errors, "incrementaldecoder");
        }
    }
}
