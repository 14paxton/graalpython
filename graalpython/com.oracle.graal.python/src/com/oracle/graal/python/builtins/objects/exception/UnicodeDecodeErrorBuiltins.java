/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_ENCODING;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_END;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_OBJECT;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_REASON;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_START;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.UNICODE_ERROR_ATTR_FACTORY;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsInt;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsObject;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsString;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnicodeDecodeError)
public final class UnicodeDecodeErrorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnicodeDecodeErrorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class UnicodeDecodeErrorInitNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PBaseException self, Object[] args);

        @Specialization
        Object initNoArgs(PBaseException self, Object[] args,
                        @Cached CastToJavaStringNode toJavaStringNode,
                        @Cached CastToJavaIntExactNode toJavaIntExactNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode) {
            baseInitNode.execute(self, args);
            // PyArg_ParseTuple(args, "UOnnU"), TODO: add proper error messages
            self.setExceptionAttributes(new Object[]{
                            getArgAsString(args, 0, this, toJavaStringNode),
                            getArgAsObject(args, 1, this),
                            getArgAsInt(args, 2, this, toJavaIntExactNode),
                            getArgAsInt(args, 3, this, toJavaIntExactNode),
                            getArgAsString(args, 4, this, toJavaStringNode)
            });
            return PNone.NONE;
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class UnicodeEncodeErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectStrAsJavaStringNode strNode) {
            if (self.getExceptionAttributes() == null) {
                // Not properly initialized.
                return "";
            }

            // Get reason and encoding as strings, which they might not be if they've been
            // modified after we were constructed.
            Object object = attrNode.get(self, IDX_OBJECT, UNICODE_ERROR_ATTR_FACTORY);
            final int start = attrNode.getInt(self, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
            final int end = attrNode.getInt(self, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
            final String encoding = strNode.execute(frame, attrNode.get(self, IDX_ENCODING, UNICODE_ERROR_ATTR_FACTORY));
            final String reason = strNode.execute(frame, attrNode.get(self, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY));
            if (start < sizeNode.execute(frame, object) && end == start + 1) {
                final int b = (int) getItem.execute(frame, object, 0);
                return PythonUtils.format("'%s' codec can't decode byte 0x%02x in position %d: %s", encoding, b, start, reason);
            } else {
                return PythonUtils.format("'%s' codec can't decode bytes in position %d-%d: %s", encoding, start, end - 1, reason);
            }
        }
    }
}
