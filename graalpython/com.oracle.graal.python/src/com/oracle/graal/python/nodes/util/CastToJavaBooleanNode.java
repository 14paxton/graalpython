/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Casts a Python boolean to a Java boolean without coercion. <b>ATTENTION:</b> If the cast fails,
 * because the object is not a Python boolean, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(PGuards.class)
public abstract class CastToJavaBooleanNode extends PNodeWithContext {

    public abstract boolean execute(Node inliningTarget, Object x) throws CannotCastException;

    public static boolean executeUncached(Object x) throws CannotCastException {
        return CastToJavaBooleanNodeGen.getUncached().execute(null, x);
    }

    @Specialization
    static boolean doBoolean(boolean x) {
        return x;
    }

    @Specialization
    static boolean doPInt(Node inliningTarget, PInt x,
                    // DSL generates better code if all are @Shared
                    @Shared("dummy") @Cached InlinedConditionProfile isBoolean,
                    @Shared @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                    @Shared @Cached GetClassNode getClassNode) {
        if (isBoolean.profile(inliningTarget, isSubtypeNode.execute(getClassNode.execute(inliningTarget, x), PythonBuiltinClassType.Boolean))) {
            return !x.isZero();
        } else {
            throw CannotCastException.INSTANCE;
        }
    }

    @Specialization
    @HostCompilerDirectives.InliningCutoff
    static boolean doNativeObject(Node inliningTarget, PythonNativeObject x,
                    @SuppressWarnings("unused") @Shared("dummy") @Cached InlinedConditionProfile isBoolean,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached(inline = false) IsSubtypeNode isSubtypeNode) {
        if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, x), PythonBuiltinClassType.Boolean)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, ErrorMessages.CASTING_A_NATIVE_INT_OBJECT_IS_NOT_IMPLEMENTED_YET);
        }
        // the object's type is not a subclass of 'int'
        throw CannotCastException.INSTANCE;
    }

    @Fallback
    static boolean doUnsupported(@SuppressWarnings("unused") Object x) {
        throw CannotCastException.INSTANCE;
    }
}
