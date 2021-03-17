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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotGuards;
import com.oracle.graal.python.nodes.frame.FrameSlotNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ValueProfile;

@NodeChild(value = "rightNode", type = ExpressionNode.class)
@ImportStatic(FrameSlotGuards.class)
public abstract class WriteGeneratorFrameVariableNode extends StatementNode implements WriteIdentifierNode, FrameSlotNode {

    protected final FrameSlot frameSlot;
    private final ValueProfile frameProfile = ValueProfile.createClassProfile();

    public WriteGeneratorFrameVariableNode(FrameSlot frameSlot) {
        this.frameSlot = frameSlot;
    }

    public static WriteGeneratorFrameVariableNode create(FrameSlot frameSlot, ExpressionNode right) {
        return WriteGeneratorFrameVariableNodeGen.create(frameSlot, right);
    }

    public abstract ExpressionNode getRightNode();

    @Override
    public final ExpressionNode getRhs() {
        return getRightNode();
    }

    @Override
    public final FrameSlot getSlot() {
        return frameSlot;
    }

    @Override
    public final Object getIdentifier() {
        return frameSlot.getIdentifier();
    }

    protected Frame getGeneratorFrame(VirtualFrame frame) {
        return frameProfile.profile(PArguments.getGeneratorFrame(frame));
    }

    @Specialization(guards = "isBooleanKind(getGeneratorFrame(frame), frameSlot)")
    @Override
    public void writeBoolean(VirtualFrame frame, boolean value) {
        getGeneratorFrame(frame).setBoolean(frameSlot, value);
    }

    @Specialization(guards = "isIntegerKind(getGeneratorFrame(frame), frameSlot)")
    @Override
    public void writeInt(VirtualFrame frame, int value) {
        getGeneratorFrame(frame).setInt(frameSlot, value);
    }

    @Specialization(guards = "isLongKind(getGeneratorFrame(frame), frameSlot)")
    @Override
    public void writeLong(VirtualFrame frame, long value) {
        getGeneratorFrame(frame).setLong(frameSlot, value);
    }

    @Specialization(guards = "isDoubleKind(getGeneratorFrame(frame), frameSlot)")
    @Override
    public void writeDouble(VirtualFrame frame, double value) {
        getGeneratorFrame(frame).setDouble(frameSlot, value);
    }

    @Specialization(replaces = {"writeBoolean", "writeInt", "writeLong", "writeDouble"})
    @Override
    public void writeObject(VirtualFrame frame, Object value) {
        Frame generatorFrame = getGeneratorFrame(frame);
        FrameSlotGuards.ensureObjectKind(generatorFrame, frameSlot);
        generatorFrame.setObject(frameSlot, value);
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }
}
