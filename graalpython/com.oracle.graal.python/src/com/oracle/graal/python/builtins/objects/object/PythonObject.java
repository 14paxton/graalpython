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
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.HiddenAttributes.CLASS;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenPythonKey;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.HiddenAttributes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public class PythonObject extends PythonAbstractObject {
    public static final HiddenKey DICT = HiddenAttributes.DICT;
    private static final byte CLASS_CHANGED_FLAG = 1;
    public static final byte HAS_SLOTS_BUT_NO_DICT_FLAG = 2;

    private final Object initialPythonClass;

    public PythonObject(Object pythonClass, Shape instanceShape) {
        super(instanceShape);
        assert pythonClass != null;
        assert consistentStorage(pythonClass);
        this.initialPythonClass = pythonClass;
    }

    private boolean consistentStorage(Object pythonClass) {
        DynamicObjectLibrary dylib = DynamicObjectLibrary.getUncached();
        Object constantClass = dylib.getOrDefault(this, CLASS, null);
        if (constantClass == null) {
            return true;
        }
        if (constantClass instanceof PythonBuiltinClass) {
            constantClass = ((PythonBuiltinClass) constantClass).getType();
        }
        return constantClass == (pythonClass instanceof PythonBuiltinClass ? ((PythonBuiltinClass) pythonClass).getType() : pythonClass);
    }

    @ExportMessage
    public void setLazyPythonClass(Object cls,
                    @Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib, @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            // n.b.: the CLASS property is usually a constant property that is stored in the shape
            // in
            // single-context-mode. If we change it for the first time, there's an implicit shape
            // transition
            dylib.setShapeFlags(this, dylib.getShapeFlags(this) | CLASS_CHANGED_FLAG);
            dylib.put(this, CLASS, cls);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public static class GetLazyPythonClass {
        public static boolean hasInitialClass(PythonObject self, DynamicObjectLibrary dylib) {
            return (dylib.getShapeFlags(self) & CLASS_CHANGED_FLAG) == 0;
        }

        public static Object getInitialClass(PythonObject self) {
            return self.initialPythonClass;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"klass != null", "self.getShape() == cachedShape", "hasInitialClass(self, dylib)"}, limit = "1", assumptions = "singleContextAssumption()")
        public static Object getConstantClass(PythonObject self,
                        @Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib,
                        @Cached("self.getShape()") Shape cachedShape,
                        @Cached(value = "getInitialClass(self)", weak = true) Object klass, @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return klass;
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(replaces = "getConstantClass")
        public static Object getPythonClass(PythonObject self,
                        @Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib, @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return dylib.getOrDefault(self, CLASS, self.initialPythonClass);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    public final DynamicObject getStorage() {
        return this;
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public final Object getAttribute(Object key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(getStorage(), key, PNone.NO_VALUE);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public void setAttribute(Object name, Object value) {
        assert name instanceof String || name instanceof HiddenPythonKey || name instanceof HiddenKey : name.getClass().getSimpleName();
        CompilerAsserts.neverPartOfCompilation();
        DynamicObjectLibrary.getUncached().put(getStorage(), name, value);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public List<String> getAttributeNames() {
        ArrayList<String> keyList = new ArrayList<>();
        for (Object o : getStorage().getShape().getKeyList()) {
            if (o instanceof String && DynamicObjectLibrary.getUncached().getOrDefault(getStorage(), o, PNone.NO_VALUE) != PNone.NO_VALUE) {
                keyList.add((String) o);
            }
        }
        return keyList;
    }

    @Override
    public int compareTo(Object o) {
        return this == o ? 0 : 1;
    }

    /**
     * Important: toString can be called from arbitrary locations, so it cannot do anything that may
     * execute Python code or rely on a context being available.
     *
     * The Python-level string representation functionality always needs to be implemented as
     * __repr__/__str__ builtins (which in turn can call toString if this already implements the
     * correct behavior).
     */
    @Override
    public String toString() {
        String className = "unknown";
        Object storedPythonClass = DynamicObjectLibrary.getUncached().getOrDefault(this, CLASS, null);
        if (storedPythonClass instanceof PythonManagedClass) {
            className = ((PythonManagedClass) storedPythonClass).getQualName();
        } else if (storedPythonClass instanceof PythonBuiltinClassType) {
            className = ((PythonBuiltinClassType) storedPythonClass).getName();
        } else if (PGuards.isNativeClass(storedPythonClass)) {
            className = "native";
        }
        return "<" + className + " object at 0x" + Integer.toHexString(hashCode()) + ">";
    }

    @ExportMessage
    public boolean hasDict(@Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib, @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return dylib.containsKey(this, DICT);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public PDict getDict(@Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib, @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return (PDict) dylib.getOrDefault(this, DICT, null);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public final void setDict(PDict dict,
                    @Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib, @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            dylib.put(this, DICT, dict);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public final void deleteDict(@Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib, @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            dylib.put(this, DICT, null);
        } finally {
            gil.release(mustRelease);
        }
    }

    /* needed for some guards in exported messages of subclasses */
    public static int getCallSiteInlineCacheMaxDepth() {
        return PythonOptions.getCallSiteInlineCacheMaxDepth();
    }
}
