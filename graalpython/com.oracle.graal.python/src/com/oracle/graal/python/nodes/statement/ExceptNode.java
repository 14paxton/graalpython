/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateWrapper
public class ExceptNode extends PNodeWithContext implements InstrumentableNode {
    @Child private StatementNode body;
    @Child private WriteNode exceptName;
    @Child private ExpressionNode exceptType;

    @Child private ExceptMatchNode matchNode;

    public ExceptNode(StatementNode body, ExpressionNode exceptType, WriteNode exceptName) {
        this.body = body;
        this.exceptName = exceptName;
        this.exceptType = exceptType;
    }

    public ExceptNode(ExceptNode original) {
        this.body = original.body;
        this.exceptName = original.exceptName;
        this.exceptType = original.exceptType;
    }

    public void executeExcept(VirtualFrame frame, Throwable e) {
        if (exceptName != null) {
            if (e instanceof PException) {
                exceptName.doWrite(frame, ((PException) e).getEscapedException());
            } else {
                exceptName.doWrite(frame, e);
            }
        }
        body.executeVoid(frame);
        if (exceptName != null) {
            exceptName.doWrite(frame, null);
        }
        throw ExceptionHandledException.INSTANCE;
    }

    public boolean matchesPException(VirtualFrame frame, PException e) {
        if (exceptType == null) {
            return true;
        }
        return getMatchNode().executeMatch(frame, e, exceptType.execute(frame));
    }

    public boolean matchesTruffleException(@SuppressWarnings("unused") VirtualFrame frame, AbstractTruffleException e) {
        assert !(e instanceof PException);
        // TODO: (tfel) should we allow catching with the meta-object of arbitrary truffle
        // exceptions?
        return exceptType == null;
    }

    public boolean matchesHostException(VirtualFrame frame, Throwable e) {
        assert !(e instanceof AbstractTruffleException);
        if (exceptType == null) {
            return false; // host exceptions must be matched explicitly
        }
        return getMatchNode().executeMatch(frame, e, exceptType.execute(frame));
    }

    private ExceptMatchNode getMatchNode() {
        if (matchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            matchNode = insert(ExceptMatchNode.create());
        }
        return matchNode;
    }

    public StatementNode getBody() {
        return body;
    }

    public ExpressionNode getExceptType() {
        return exceptType;
    }

    public WriteNode getExceptName() {
        return exceptName;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new ExceptNodeWrapper(this, this, probeNode);
    }

    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }
}

interface EmulateJythonNode {
    default boolean emulateJython(PythonLanguage language) {
        return language.getEngineOption(PythonOptions.EmulateJython);
    }
}

@ImportStatic(PythonOptions.class)
abstract class ValidExceptionNode extends Node implements EmulateJythonNode {
    protected abstract boolean execute(VirtualFrame frame, Object type);

    protected static boolean isPythonExceptionType(PythonBuiltinClassType type) {
        PythonBuiltinClassType base = type;
        while (base != null) {
            if (base == PythonBuiltinClassType.PBaseException) {
                return true;
            }
            base = base.getBase();
        }
        return false;
    }

    @Specialization(guards = "cachedType == type", limit = "3")
    boolean isPythonExceptionTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType type,
                    @SuppressWarnings("unused") @Cached("type") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(type)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "cachedType == klass.getType()", limit = "3")
    boolean isPythonExceptionClassCached(@SuppressWarnings("unused") PythonBuiltinClass klass,
                    @SuppressWarnings("unused") @Cached("klass.getType()") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(cachedType)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "lib.isLazyPythonClass(type)", replaces = {"isPythonExceptionTypeCached", "isPythonExceptionClassCached"})
    boolean isPythonException(VirtualFrame frame, Object type,
                    @Cached IsSubtypeNode isSubtype,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
        return isSubtype.execute(frame, type, PythonBuiltinClassType.PBaseException);
    }

    @Specialization(guards = {"emulateJython(language)", "context.getEnv().isHostObject(type)"})
    @SuppressWarnings("unused")
    boolean isJavaException(@SuppressWarnings("unused") VirtualFrame frame, Object type,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        Object hostType = context.getEnv().asHostObject(type);
        return hostType instanceof Class && Throwable.class.isAssignableFrom((Class<?>) hostType);
    }

    @Fallback
    boolean isAnException(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object type) {
        return false;
    }

    static ValidExceptionNode create() {
        return ValidExceptionNodeGen.create();
    }
}

@ImportStatic({PGuards.class, PythonOptions.class})
abstract class ExceptMatchNode extends Node implements EmulateJythonNode {
    @Child private PRaiseNode raiseNode;

    protected abstract boolean executeMatch(VirtualFrame frame, Object exception, Object clause);

    private void raiseIfNoException(VirtualFrame frame, Object clause, ValidExceptionNode isValidException) {
        if (!isValidException.execute(frame, clause)) {
            raiseNoException();
        }
    }

    private void raiseNoException() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.CATCHING_CLS_NOT_ALLOWED);
    }

    @Specialization(guards = "isClass(clause, lib)", limit = "3")
    boolean matchPythonSingle(VirtualFrame frame, PException e, Object clause,
                    @SuppressWarnings("unused") @CachedLibrary("clause") InteropLibrary lib,
                    @Cached ValidExceptionNode isValidException,
                    @CachedLibrary("e.getUnreifiedException()") PythonObjectLibrary plib,
                    @Cached IsSubtypeNode isSubtype) {
        raiseIfNoException(frame, clause, isValidException);
        return isSubtype.execute(frame, plib.getLazyPythonClass(e.getUnreifiedException()), clause);
    }

    @Specialization(guards = {"emulateJython(language)", "context.getEnv().isHostException(e)", "context.getEnv().isHostObject(clause)"})
    @SuppressWarnings("unused")
    boolean matchJava(VirtualFrame frame, Throwable e, Object clause,
                    @Cached ValidExceptionNode isValidException,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        raiseIfNoException(frame, clause, isValidException);
        // cast must succeed due to ValidExceptionNode above
        Class<?> javaClause = (Class<?>) context.getEnv().asHostObject(clause);
        Throwable hostException = context.getEnv().asHostException(e);
        return javaClause.isInstance(hostException);
    }

    @Specialization(guards = {"emulateJython(language)", "context.getEnv().isHostObject(clause)"})
    @SuppressWarnings("unused")
    boolean doNotMatchPython(VirtualFrame frame, @SuppressWarnings("unused") PException e, Object clause,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @Cached ValidExceptionNode isValidException) {
        raiseIfNoException(frame, clause, isValidException);
        return false;
    }

    @Specialization(guards = {"lib.isLazyPythonClass(clause)", "emulateJython(language)", "context.getEnv().isHostException(e)"})
    @SuppressWarnings("unused")
    boolean doNotMatchJava(VirtualFrame frame, @SuppressWarnings("unused") Throwable e, Object clause,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @Cached ValidExceptionNode isValidException,
                    @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
        raiseIfNoException(frame, clause, isValidException);
        return false;
    }

    @Specialization
    boolean matchTuple(VirtualFrame frame, Object e, PTuple clause,
                    @Cached ExceptMatchNode recursiveNode,
                    @Cached SequenceStorageNodes.LenNode getLenNode,
                    @Cached SequenceStorageNodes.GetItemNode getItemNode) {
        // check for every type in the tuple
        SequenceStorage storage = clause.getSequenceStorage();
        int length = getLenNode.execute(storage);
        for (int i = 0; i < length; i++) {
            Object clauseType = getItemNode.execute(frame, storage, i);
            if (recursiveNode.executeMatch(frame, e, clauseType)) {
                return true;
            }
        }
        return false;
    }

    @Fallback
    @SuppressWarnings("unused")
    boolean fallback(VirtualFrame frame, Object e, Object clause) {
        raiseNoException();
        return false;
    }

    static ExceptMatchNode create() {
        return ExceptMatchNodeGen.create();
    }
}
