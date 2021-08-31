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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArray;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArrayType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtrType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointerType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCSimpleType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCStructType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnionType;
import static com.oracle.graal.python.nodes.ErrorMessages.ARRAY_LENGTH_MUST_BE_0_NOT_D;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_A_TYPE_OBJECT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesThreadState;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = {
                PyCStructType,
                UnionType,
                PyCArrayType,
                PyCFuncPtrType,
                PyCPointerType,
                PyCSimpleType,
})
public class CDataTypeSequenceBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CDataTypeSequenceBuiltinsFactory.getFactories();
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class RepeatNode extends PythonBinaryBuiltinNode {
        // TODO: weakref ctypes.cache values
        @Specialization(guards = "length >= 0")
        Object PyCArrayType_from_ctype(VirtualFrame frame, Object itemtype, int length,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @Cached CallNode callNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached GetNameNode getNameNode) {
            Object key = factory().createTuple(new Object[]{itemtype, length});
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), getLanguage());
            Object result = hlib.getItem(ctypes.cache, key);
            if (result != null) {
                return result;
            }

            if (!isTypeNode.execute(itemtype)) {
                throw raise(TypeError, EXPECTED_A_TYPE_OBJECT);
            }
            String name = PythonUtils.format("%s_Array_%d", getNameNode.execute(itemtype), length);
            PDict dict = factory().createDict(new PKeyword[]{new PKeyword("_length_", length), new PKeyword("_type_", itemtype)});
            PTuple tuple = factory().createTuple(new Object[]{PyCArray});
            result = callNode.execute(frame, PyCArrayType, name, tuple, dict);
            hlib.setItem(ctypes.cache, key, result);
            return result;
        }

        @Specialization(guards = "length < 0")
        Object error(@SuppressWarnings("unused") Object self, int length) {
            throw raise(ValueError, ARRAY_LENGTH_MUST_BE_0_NOT_D, length);
        }
    }
}
