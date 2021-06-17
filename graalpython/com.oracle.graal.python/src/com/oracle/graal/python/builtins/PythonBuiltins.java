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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.util.BiConsumer;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;

public abstract class PythonBuiltins {
    protected final Map<Object, Object> builtinConstants = new HashMap<>();
    private final Map<String, BoundBuiltinCallable<?>> builtinFunctions = new HashMap<>();
    private final Map<PythonBuiltinClass, Map.Entry<PythonBuiltinClassType[], Boolean>> builtinClasses = new HashMap<>();

    protected abstract List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories();

    /**
     * Initialize everything that is truly independent of commandline arguments and that can be
     * initialized and frozen into an SVM image. When in a subclass, any modifications to
     * {@link #builtinConstants} or such should be made before calling
     * {@code super.initialize(core)}.
     */
    public void initialize(Python3Core core) {
        if (builtinFunctions.size() > 0) {
            return;
        }
        initializeEachFactoryWith((factory, builtin) -> {
            CoreFunctions annotation = getClass().getAnnotation(CoreFunctions.class);
            final boolean declaresExplicitSelf;
            PythonBuiltinClassType constructsClass = builtin.constructsClass();
            if (annotation.defineModule().length() > 0 && constructsClass == PythonBuiltinClassType.nil) {
                assert !builtin.isGetter();
                assert !builtin.isSetter();
                assert annotation.extendClasses().length == 0;
                // for module functions, explicit self is false by default
                declaresExplicitSelf = builtin.declaresExplicitSelf();
            } else {
                declaresExplicitSelf = true;
            }
            RootCallTarget callTarget = core.getLanguage().createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, factory, declaresExplicitSelf), factory.getNodeClass(),
                            builtin.name());
            Object builtinDoc = builtin.doc().isEmpty() ? PNone.NONE : builtin.doc();
            if (constructsClass != PythonBuiltinClassType.nil) {
                assert !builtin.isGetter() && !builtin.isSetter() && !builtin.isClassmethod() && !builtin.isStaticmethod();
                // we explicitly do not make these "staticmethods" here, since CPython also doesn't
                // for builtin types
                PBuiltinFunction newFunc = core.factory().createBuiltinFunction(__NEW__, constructsClass, numDefaults(builtin), callTarget);
                PythonBuiltinClass builtinClass = core.lookupType(constructsClass);
                builtinClass.setAttributeUnsafe(__NEW__, newFunc);
                builtinClass.setAttribute(__DOC__, builtinDoc);
            } else {
                PBuiltinFunction function = core.factory().createBuiltinFunction(builtin.name(), null, numDefaults(builtin), callTarget);
                function.setAttribute(__DOC__, builtinDoc);
                BoundBuiltinCallable<?> callable = function;
                if (builtin.isGetter() || builtin.isSetter()) {
                    assert !builtin.isClassmethod() && !builtin.isStaticmethod();
                    PBuiltinFunction get = builtin.isGetter() ? function : null;
                    PBuiltinFunction set = builtin.isSetter() ? function : null;
                    callable = core.factory().createGetSetDescriptor(get, set, builtin.name(), null, builtin.allowsDelete());
                } else if (builtin.isClassmethod()) {
                    assert !builtin.isStaticmethod();
                    callable = core.factory().createBuiltinClassmethodFromCallableObj(function);
                } else if (builtin.isStaticmethod()) {
                    callable = core.factory().createStaticmethodFromCallableObj(function);
                }
                setBuiltinFunction(builtin.name(), callable);
            }
        });
    }

    /**
     * Run any actions that can only be run in the post-initialization step, that is, if we're
     * actually going to start running rather than just pre-initializing.
     */
    public void postInitialize(@SuppressWarnings("unused") Python3Core core) {
        // nothing to do by default
    }

    private void initializeEachFactoryWith(BiConsumer<NodeFactory<? extends PythonBuiltinBaseNode>, Builtin> func) {
        List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> factories = getNodeFactories();
        assert factories != null : "No factories found. Override getFactories() to resolve this.";
        for (NodeFactory<? extends PythonBuiltinBaseNode> factory : factories) {
            Boolean needsFrame = null;
            for (Builtin builtin : factory.getNodeClass().getAnnotationsByType(Builtin.class)) {
                if (needsFrame == null) {
                    needsFrame = builtin.needsFrame();
                } else if (needsFrame != builtin.needsFrame()) {
                    throw new IllegalStateException(String.format("Implementation error in %s: all @Builtin annotations must agree if the node needs a frame.", factory.getNodeClass().getName()));
                }
                func.accept(factory, builtin);
            }
        }
    }

    private static int numDefaults(Builtin builtin) {
        String[] parameterNames = builtin.parameterNames();
        int maxNumPosArgs = Math.max(builtin.minNumOfPositionalArgs(), parameterNames.length);
        if (builtin.maxNumOfPositionalArgs() >= 0) {
            maxNumPosArgs = builtin.maxNumOfPositionalArgs();
            assert parameterNames.length == 0 : "either give all parameter names explicitly, or define the max number: " + builtin.name();
        }
        return maxNumPosArgs - builtin.minNumOfPositionalArgs();
    }

    private void setBuiltinFunction(String name, BoundBuiltinCallable<?> function) {
        builtinFunctions.put(name, function);
    }

    protected Map<String, BoundBuiltinCallable<?>> getBuiltinFunctions() {
        return builtinFunctions;
    }

    protected Map<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> getBuiltinClasses() {
        Map<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> tmp = builtinClasses;
        assert (tmp = Collections.unmodifiableMap(tmp)) != null;
        return tmp;
    }

    protected Map<Object, Object> getBuiltinConstants() {
        return builtinConstants;
    }
}
