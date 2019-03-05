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
package com.oracle.graal.python.nodes.argument.keywords;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.util.CastToStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@ImportStatic(PythonOptions.class)
@NodeChild(value = "starargs", type = ExpressionNode.class)
public abstract class ExecuteKeywordStarargsNode extends PNodeWithContext {

    public abstract PKeyword[] execute(VirtualFrame frame);

    public abstract PKeyword[] executeWith(Object starargs);

    @Specialization
    PKeyword[] doit(PKeyword[] starargs) {
        return starargs;
    }

    @Specialization(guards = "isKeywordsStorage(starargs.getDictStorage())")
    PKeyword[] doKeywordsStorage(PDict starargs) {
        return ((KeywordsStorage) starargs.getDictStorage()).getStore();
    }

    @Specialization(guards = "starargs.size() == cachedLen", limit = "getVariableArgumentInlineCacheLimit()")
    PKeyword[] cached(PDict starargs,
                    @Cached("starargs.size()") int cachedLen,
                    @Cached("create()") BranchProfile errorProfile) {
        try {
            PKeyword[] keywords = new PKeyword[starargs.size()];
            copyKeywords(starargs, cachedLen, keywords);
            return keywords;
        } catch (KeywordNotStringException e) {
            errorProfile.enter();
            throw raise(TypeError, "keywords must be strings");
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static void copyKeywords(PDict starargs, int cachedLen, PKeyword[] keywords) throws KeywordNotStringException {
        Iterator<DictEntry> iterator = starargs.entries().iterator();
        for (int i = 0; i < cachedLen; i++) {
            DictEntry entry = iterator.next();
            keywords[i] = new PKeyword(castToString(entry.getKey()), entry.getValue());
        }
    }

    protected boolean isKeywordsStorage(HashingStorage storage) {
        return storage instanceof KeywordsStorage;
    }

    @Specialization(replaces = "cached")
    @TruffleBoundary
    PKeyword[] uncached(PDict starargs,
                    @Cached("create()") BranchProfile errorProfile) {
        return cached(starargs, starargs.size(), errorProfile);
    }

    @SuppressWarnings("unused")
    @Specialization
    PKeyword[] generic(Object starargs) {
        return PKeyword.EMPTY_KEYWORDS;
    }

    private static String castToString(Object key) throws KeywordNotStringException {
        if (key instanceof String) {
            return (String) key;
        } else if (key instanceof PString) {
            return ((PString) key).getValue();
        }
        throw new KeywordNotStringException();
    }

    private static final class KeywordNotStringException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static ExecuteKeywordStarargsNode create() {
        return ExecuteKeywordStarargsNodeGen.create(null);
    }
}
