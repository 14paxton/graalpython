/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.BranchProfile;

public class WithNode extends ExceptionHandlingStatementNode {
    @Child private StatementNode body;
    @Child private WriteNode targetNode;
    @Child private ExpressionNode withContext;
    @Child private LookupSpecialMethodNode enterSpecialGetter = LookupSpecialMethodNode.create(__ENTER__);
    @Child private LookupSpecialMethodNode exitSpecialGetter = LookupSpecialMethodNode.create(__EXIT__);
    @Child private PythonObjectLibrary objectLibrary = PythonObjectLibrary.getFactory().createDispatched(1);
    @Child private CallUnaryMethodNode enterDispatch = CallUnaryMethodNode.create();
    @Child private CallQuaternaryMethodNode exitDispatch = CallQuaternaryMethodNode.create();
    @Child private CoerceToBooleanNode toBooleanNode = CoerceToBooleanNode.createIfTrueNode();
    @Child private GetClassNode getClassNode = GetClassNode.create();
    @Child private PRaiseNode raiseNode;
    @Child private GetTracebackNode getTracebackNode;

    private final BranchProfile noEnter = BranchProfile.create();
    private final BranchProfile noExit = BranchProfile.create();

    protected WithNode(WriteNode targetNode, StatementNode body, ExpressionNode withContext) {
        this.targetNode = targetNode;
        this.body = body;
        this.withContext = withContext;
    }

    public static WithNode create(ExpressionNode withContext, WriteNode targetNode, StatementNode body) {
        return new WithNode(targetNode, body, withContext);
    }

    private void applyValues(VirtualFrame frame, Object asNameValue) {
        if (targetNode == null) {
            return;
        } else {
            targetNode.doWrite(frame, asNameValue);
            return;
        }
    }

    public StatementNode getBody() {
        return body;
    }

    public WriteNode getTargetNode() {
        return targetNode;
    }

    private PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object withObject = getWithObject(frame);
        Object enterCallable = enterSpecialGetter.execute(frame, objectLibrary.getLazyPythonClass(withObject), withObject);
        if (enterCallable == PNone.NO_VALUE) {
            noEnter.enter();
            throw getRaiseNode().raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, withObject, __ENTER__);
        }
        Object exitCallable = exitSpecialGetter.execute(frame, objectLibrary.getLazyPythonClass(withObject), withObject);
        if (exitCallable == PNone.NO_VALUE) {
            noExit.enter();
            throw getRaiseNode().raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, withObject, __EXIT__);
        }
        doEnter(frame, withObject, enterCallable);
        try {
            doBody(frame);
        } catch (PException exception) {
            handleException(frame, withObject, exitCallable, exception);
            return;
        } catch (ControlFlowException e) {
            doLeave(frame, withObject, exitCallable);
            throw e;
        } catch (Throwable e) {
            PException pe = wrapJavaExceptionIfApplicable(e);
            if (pe == null) {
                throw e;
            }
            handleException(frame, withObject, exitCallable, pe);
            return;
        }
        doLeave(frame, withObject, exitCallable);
    }

    /**
     * Execute the nodes to get the with object.
     */
    protected Object getWithObject(VirtualFrame frame) {
        return withContext.execute(frame);
    }

    /**
     * Execute the body
     */
    protected void doBody(VirtualFrame frame) {
        body.executeVoid(frame);
    }

    /**
     * Leave the with-body. Call __exit__ if it hasn't already happened because of an exception, and
     * reset the exception state.
     */
    protected void doLeave(VirtualFrame frame, Object withObject, Object exitCallable) {
        exitDispatch.execute(frame, exitCallable, withObject, PNone.NONE, PNone.NONE, PNone.NONE);
    }

    /**
     * Call the __enter__ method
     */
    protected void doEnter(VirtualFrame frame, Object withObject, Object enterCallable) {
        applyValues(frame, enterDispatch.executeObject(frame, enterCallable, withObject));
    }

    /**
     * Call __exit__ to handle the exception
     */
    protected void handleException(VirtualFrame frame, Object withObject, Object exitCallable, PException pException) {
        PBaseException caughtException = pException.setCatchingFrameAndGetEscapedException(frame, this);
        tryChainPreexistingException(frame, caughtException);
        ExceptionState savedExceptionState = saveExceptionState(frame);
        SetCaughtExceptionNode.execute(frame, pException);
        Object type = getClassNode.execute(caughtException);
        LazyTraceback caughtTraceback = caughtException.getTraceback();
        PTraceback tb = getTraceback(caughtTraceback);
        // If exit handler returns 'true', suppress
        boolean handled;
        try {
            Object returnValue = exitDispatch.execute(frame, exitCallable, withObject, type, caughtException, tb != null ? tb : PNone.NONE);
            handled = toBooleanNode.executeBoolean(frame, returnValue);
        } catch (PException handlerException) {
            tryChainExceptionFromHandler(handlerException, pException);
            throw handlerException;
        } catch (Throwable e) {
            PException handlerException = wrapJavaExceptionIfApplicable(e);
            if (handlerException == null) {
                throw e;
            }
            tryChainExceptionFromHandler(handlerException, pException);
            throw handlerException.getExceptionForReraise();
        } finally {
            restoreExceptionState(frame, savedExceptionState);
        }
        if (!handled) {
            // re-raise exception
            throw caughtException.getExceptionForReraise(caughtTraceback);
        }
    }

    public ExpressionNode getWithContext() {
        return withContext;
    }

    private PTraceback getTraceback(LazyTraceback tb) {
        if (tb != null) {
            if (getTracebackNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTracebackNode = insert(GetTracebackNode.create());
            }
            return getTracebackNode.execute(tb);
        }
        return null;
    }
}
