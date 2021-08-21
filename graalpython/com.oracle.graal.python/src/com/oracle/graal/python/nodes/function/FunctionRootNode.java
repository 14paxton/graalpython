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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PClosureFunctionRootNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.util.Function;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

/**
 * RootNode of a Python Function body. It is invoked by a CallTarget.
 */
public class FunctionRootNode extends PClosureFunctionRootNode {
    private final ExecutionCellSlots executionCellSlots;
    private final String functionName;
    private final SourceSection sourceSection;
    private final boolean isGenerator;
    private final ValueProfile generatorFrameProfile;
    private final ExpressionNode doc;

    @Child private ExpressionNode body;
    @Child private CalleeContext calleeContext = CalleeContext.create();

    private final ExpressionNode uninitializedBody;
    private boolean isPythonInternal;

    public FunctionRootNode(PythonLanguage language, SourceSection sourceSection, String functionName, boolean isGenerator, boolean isRewritten, FrameDescriptor frameDescriptor,
                    ExpressionNode uninitializedBody, ExecutionCellSlots executionCellSlots, Signature signature, ExpressionNode doc) {
        super(language, frameDescriptor, executionCellSlots, signature);
        this.executionCellSlots = executionCellSlots;

        this.sourceSection = sourceSection;
        assert sourceSection != null;
        this.functionName = functionName;
        this.isGenerator = isGenerator;
        this.body = new InnerRootNode(this, NodeUtil.cloneNode(uninitializedBody));
        // "uninitializedBody" is never modified or executed
        this.uninitializedBody = uninitializedBody;
        this.generatorFrameProfile = isGenerator ? ValueProfile.createClassProfile() : null;
        this.isPythonInternal = isRewritten;
        this.doc = doc;
    }

    /**
     * Creates a shallow copy.
     */
    private FunctionRootNode(FunctionRootNode other) {
        super(PythonLanguage.get(other), other.getFrameDescriptor(), other.executionCellSlots, other.getSignature());
        this.executionCellSlots = other.executionCellSlots;

        this.sourceSection = other.getSourceSection();
        this.functionName = other.functionName;
        this.isGenerator = other.isGenerator;
        this.generatorFrameProfile = other.isGenerator ? ValueProfile.createClassProfile() : null;
        this.isPythonInternal = other.isPythonInternal;
        this.uninitializedBody = other.uninitializedBody;
        this.doc = other.doc;
    }

    @Override
    protected boolean isCloneUninitializedSupported() {
        return true;
    }

    @Override
    protected RootNode cloneUninitialized() {
        return new FunctionRootNode(PythonLanguage.getCurrent(), getSourceSection(), functionName, isGenerator, isPythonInternal, getFrameDescriptor(), uninitializedBody, executionCellSlots,
                        getSignature(), doc);
    }

    /**
     * Returns a new function that has its signature replaced and whose body has been modified by
     * the given node visitor.
     */
    public FunctionRootNode rewriteWithNewSignature(Signature newSignature, NodeVisitor nodeVisitor, Function<ExpressionNode, ExpressionNode> bodyFun) {
        ExpressionNode newUninitializedBody = bodyFun.apply(NodeUtil.cloneNode(uninitializedBody));
        newUninitializedBody.accept(nodeVisitor);
        return new FunctionRootNode(PythonLanguage.getCurrent(), getSourceSection(), functionName, isGenerator, true, getFrameDescriptor(), newUninitializedBody, executionCellSlots,
                        newSignature, doc);
    }

    public boolean isLambda() {
        return functionName.equals(BuiltinNames.LAMBDA_NAME);
    }

    @Override
    public String getName() {
        return functionName;
    }

    public FrameSlot[] getCellVarSlots() {
        return cellVarSlots;
    }

    @Override
    public FunctionRootNode copy() {
        return new FunctionRootNode(this);
    }

    @ExplodeLoop
    private void initializeCellVars(Frame frame) {
        for (int i = 0; i < cellVarSlots.length; i++) {
            FrameSlot frameSlot = cellVarSlots[i];

            // get the cell
            PCell cell = null;
            if (isGenerator) {
                cell = (PCell) FrameUtil.getObjectSafe(frame, frameSlot);
            }
            if (cell == null) {
                cell = new PCell(cellEffectivelyFinalAssumptions[i]);
            }

            // store the cell as a local var
            frame.setObject(frameSlot, cell);
        }
    }

    private void initClosureAndCellVars(VirtualFrame frame) {
        Frame accessingFrame = frame;
        if (isGenerator) {
            accessingFrame = generatorFrameProfile.profile(PArguments.getGeneratorFrame(frame));
        }

        addClosureCellsToLocals(accessingFrame);
        initializeCellVars(accessingFrame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        calleeContext.enter(frame);
        try {
            return body.execute(frame);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<function root " + functionName + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public boolean isInternal() {
        return sourceSection != null && sourceSection.getSource().isInternal();
    }

    @Override
    public void initializeFrame(VirtualFrame frame) {
        initClosureAndCellVars(frame);
    }

    @Override
    public boolean isPythonInternal() {
        return isPythonInternal;
    }

    public void setPythonInternal(boolean pythonInternal) {
        isPythonInternal = pythonInternal;
    }

    public ExecutionCellSlots getExecutionCellSlots() {
        return executionCellSlots;
    }

    public ExpressionNode getDoc() {
        return doc;
    }

}
