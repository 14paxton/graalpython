/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_ADD;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_AND;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_INDEX;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_INPLACE_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_POW;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_TRUE_DIVIDE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyNumberMethods}.
 */
@ExportLibrary(InteropLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class PyNumberMethodsWrapper extends PythonNativeWrapper {

    public PyNumberMethodsWrapper(PythonManagedClass delegate) {
        super(delegate);
    }

    public PythonManagedClass getPythonClass() {
        return (PythonManagedClass) getDelegate();
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        return isValidMember(member);
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Exclusive @Cached ReadMethodNode readMethodNode,
                    @Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) throws UnknownIdentifierException {
        // translate key to attribute name
        return toSulongNode.execute(readMethodNode.execute(getPythonClass(), member));
    }

    @GenerateUncached
    @ImportStatic(PyNumberMethodsWrapper.class)
    abstract static class ReadMethodNode extends Node {

        public abstract Object execute(PythonManagedClass clazz, String key) throws UnknownIdentifierException;

        @Specialization(limit = "99", guards = {"isValidMember(key)", "eq(cachedKey, key)"})
        Object getMethod(PythonManagedClass clazz, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Exclusive @Cached LookupAttributeInMRONode.Dynamic lookupNode) throws UnknownIdentifierException {
            Object result = lookupNode.execute(clazz, translate(cachedKey));
            assert result != PNone.NO_VALUE;
            return result;
        }

        private static String translate(String key) throws UnknownIdentifierException {
            switch (key) {
                case NB_ADD:
                    return __ADD__;
                case NB_AND:
                    return __AND__;
                case NB_INDEX:
                    return __INDEX__;
                case NB_POW:
                    return __POW__;
                case NB_TRUE_DIVIDE:
                    return __TRUEDIV__;
                case NB_MULTIPLY:
                    return __MUL__;
                case NB_INPLACE_MULTIPLY:
                    return __IMUL__;
                default:
                    // TODO extend list according to 'isValidMember'
                    throw UnknownIdentifierException.create(key);
            }
        }

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }
    }

    protected static boolean isValidMember(String member) {
        switch (member) {
            case NB_ADD:
            case NB_AND:
            case NB_INDEX:
            case NB_POW:
            case NB_TRUE_DIVIDE:
            case NB_MULTIPLY:
            case NB_INPLACE_MULTIPLY:
                return true;
            default:
                return false;
        }
    }
}
