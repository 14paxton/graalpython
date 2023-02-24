/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.CImpl;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.NotImplemented;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VARARGS;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VoidNoReturn;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * This class generates the contents of the {@link PythonCextBuiltinRegistry} class and the code
 * needed on the native side to define all function symbols and to forward calls to the Java/Sulong
 * side. The codegen process also checks whether there are undefined or extraneous C API functions.
 */
public final class CApiCodeGen {

    private static final String START_CAPI_BUILTINS = "{{start CAPI_BUILTINS}}";
    private static final String END_CAPI_BUILTINS = "{{end CAPI_BUILTINS}}";

    public static final class CApiBuiltinDesc {
        public final String name;
        public final boolean inlined;
        public final ArgDescriptor[] arguments;
        public final ArgDescriptor returnType;
        public final CApiCallPath call;
        public final String forwardsTo;
        public final String factory;
        public int id;

        public CApiBuiltinDesc(String name, boolean inlined, ArgDescriptor returnType, ArgDescriptor[] arguments, CApiCallPath call, String forwardsTo, String factory) {
            this.name = name;
            this.inlined = inlined;
            this.returnType = returnType;
            this.arguments = arguments;
            this.call = call;
            this.forwardsTo = forwardsTo;
            this.factory = factory;
        }

        boolean hasVarargs() {
            return Arrays.stream(arguments).anyMatch(a -> a == ArgDescriptor.VARARGS);
        }

        void generateUnimplemented(List<String> lines) {
            lines.add((inlined ? "MUST_INLINE " : "") + "PyAPI_FUNC(" + returnType.getCSignature() + ") " + name + (inlined ? "_Inlined" : "") +
                            "(" + mapArgs(i -> getArgSignatureWithName(arguments[i], i), ", ") + ") {");
            lines.add("    unimplemented(\"" + name + "\"); exit(-1);");
            lines.add("}");
        }

        void generateC(List<String> lines) {
            lines.add(returnType.getCSignature() + " (*" + targetName() + ")(" + mapArgs(i -> arguments[i].getCSignature(), ", ") + ") = NULL;");
            lines.add((inlined ? "MUST_INLINE " : "") + "PyAPI_FUNC(" + returnType.getCSignature() + ") " + name + (inlined ? "_Inlined" : "") +
                            "(" + mapArgs(i -> getArgSignatureWithName(arguments[i], i), ", ") + ") {");
            String line = "    ";
            if (!returnType.isVoid()) {
                line += returnType.getCSignature() + " result = (" + returnType.getCSignature() + ") ";
            }
            lines.add(line + targetName() + "(" + mapArgs(i -> argName(i), ", ") + ");");

            if (returnType.isVoid()) {
                if (returnType == VoidNoReturn) {
                    lines.add("    abort();");
                }
            } else {
                lines.add("    return result;");
            }
            lines.add("}");
        }

        private void generateVarargForward(List<String> lines, String forwardFunction) {
            lines.add("PyAPI_FUNC(" + returnType.getCSignature() + ") " + name + "(" + mapArgs(i -> getArgSignatureWithName(arguments[i], i), ", ") + ") {");
            lines.add("    va_list args;");
            lines.add("    va_start(args, " + argName(arguments.length - 2) + ");");
            String line = "    ";
            if (!returnType.isVoid()) {
                line += returnType.getCSignature() + " result = (" + returnType.getCSignature() + ") ";
            }
            lines.add(line + forwardFunction + "(" + mapArgs(i -> i == arguments.length - 1 ? "args" : argName(i), ", ") + ");");
            lines.add("    va_end(args);");
            if (returnType.isVoid()) {
                if (returnType == VoidNoReturn) {
                    lines.add("    abort();");
                }
            } else {
                lines.add("    return result;");
            }
            lines.add("}");
        }

        public static String getArgSignatureWithName(ArgDescriptor arg, int i) {
            if (arg == VARARGS) {
                return arg.cSignature;
            }
            String sig = arg.getCSignature();
            if (sig.contains("(*)")) {
                // function type
                return sig.replace("(*)", "(*" + argName(i) + ")");
            } else if (sig.endsWith("[]")) {
                return sig.substring(0, sig.length() - 2) + argName(i) + "[]";
            } else {
                return arg.getCSignature() + " " + argName(i);
            }
        }

        private String mapArgs(IntFunction<String> fun, String delim) {
            return IntStream.range(0, arguments.length).mapToObj(fun).collect(joining(delim));
        }

        private String targetName() {
            return "__target__" + name;
        }
    }

    /**
     * Looks for the given (relative) path, assuming that the current working directory is either
     * the repository root or a project directory.
     */
    private static Path resolvePath(Path path) {
        Path result = Path.of("graalpython").resolve(path);
        if (result.toFile().exists()) {
            return result;
        }
        result = Path.of("..").resolve(path);
        if (result.toFile().exists()) {
            return result;
        }
        throw new RuntimeException("not found: " + path);
    }

    /**
     * Updates the given file by replacing the lines between the start/end markers with the given
     * lines.
     *
     * @return true if the file was modified, false if there were no changes
     */
    private static boolean writeGenerated(Path path, List<String> contents) throws IOException {
        Path capi = CApiCodeGen.resolvePath(path);
        List<String> lines = Files.readAllLines(capi);
        int start = -1;
        int end = -1;
        String prefix = "";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(START_CAPI_BUILTINS)) {
                assert start == -1;
                start = i + 1;
                prefix = lines.get(i).substring(0, lines.get(i).indexOf(START_CAPI_BUILTINS));
            } else if (lines.get(i).contains(END_CAPI_BUILTINS)) {
                assert end == -1;
                end = i;
            }
        }
        assert start != -1 && end != -1;
        List<String> result = new ArrayList<>();
        result.addAll(lines.subList(0, start));
        result.add(prefix + "GENERATED CODE - see " + CApiCodeGen.class.getSimpleName());
        result.add(prefix + "This can be re-generated using the 'mx python-capi-forwards' command or");
        result.add(prefix + "by executing the main class " + CApiCodeGen.class.getSimpleName());
        result.add("");
        result.addAll(contents);
        result.addAll(lines.subList(end, lines.size()));
        if (result.equals(lines)) {
            System.out.println("no changes for CAPI_BUILTINS in " + capi);
            return false;
        } else {
            assert result.stream().noneMatch(l -> l.contains("\n")) : "comparison fails with embedded newlines";
            Files.write(capi, result);
            System.out.println("replacing CAPI_BUILTINS in " + capi);
            return true;
        }
    }

    /**
     * Check whether the two given types are similar, based on the C signature (and ignoring a
     * "struct" keyword).
     */
    private static boolean isSimilarType(ArgDescriptor t1, ArgDescriptor t2) {
        return t1.cSignature.equals(t2.cSignature) || t1.cSignature.equals("struct " + t2.cSignature) || ("struct " + t1.cSignature).equals(t2.cSignature);
    }

    private static void compareFunction(String name, ArgDescriptor ret1, ArgDescriptor ret2, ArgDescriptor[] args1, ArgDescriptor[] args2) {
        if (!isSimilarType(ret1, ret2)) {
            System.out.println("duplicate entry for " + name + ", different return " + ret1 + " vs. " + ret2);
        }
        if (args1.length != args2.length) {
            System.out.println("duplicate entry for " + name + ", different arg lengths " + args1.length + " vs. " + args2.length);
        } else {
            for (int i = 0; i < args1.length; i++) {
                if (!isSimilarType(args1[i], args2[i])) {
                    System.out.println("duplicate entry for " + name + ", different arg " + i + ": " + args1[i] + " vs. " + args2[i]);
                }
            }
        }
    }

    private static String argName(int i) {
        return "" + (char) ('a' + i);
    }

    private static Optional<CApiBuiltinDesc> findBuiltin(List<CApiBuiltinDesc> builtins, String name) {
        return builtins.stream().filter(n -> n.name.equals(name)).findFirst();
    }

    private static List<String> MANUAL_VARARGS = Arrays.asList("PyArg_Parse", "PyObject_CallFunction", "PyObject_CallFunctionObjArgs", "PyObject_CallMethod", "PyObject_CallMethodObjArgs",
                    "PyTuple_Pack", "_PyArg_ParseStack_SizeT", "_PyArg_Parse_SizeT", "_PyObject_CallFunction_SizeT", "_PyObject_CallMethod_SizeT");

    /**
     * Generates all the forwards in capi_forwards.h, either outputting an "unimplemented" message,
     * forwarding to a "Va" version of a builtin (for varargs builtins), or simply forwarding to the
     * builtin in Sulong-executed C code.
     */
    private static boolean generateCForwards(List<CApiBuiltinDesc> builtins) throws IOException {
        System.out.println("generating C API forwards in capi_forwards.h");

        List<String> lines = new ArrayList<>();

        lines.add("// explicit #undef, some existing functions are redefined by macros and we need to export precise names:");
        for (CApiBuiltinDesc function1 : builtins) {
            lines.add("#undef " + function1.name);
        }

        TreeSet<String> missingVarargForwards = new TreeSet<>();
        LinkedHashMap<CApiBuiltinDesc, String> forwards = new LinkedHashMap<>();
        ArrayList<CApiBuiltinDesc> toBeResolved = new ArrayList<>();
        for (CApiBuiltinDesc function : builtins) {
            if (function.call == Ignored || function.call == CImpl) {
                // nothing to be done
            } else if (function.call == CApiCallPath.NotImplemented) {
                function.generateUnimplemented(lines);
            } else if (function.hasVarargs()) {
                String name = function.forwardsTo;
                Optional<CApiBuiltinDesc> existing = findBuiltin(builtins, name);
                if (existing.isPresent()) {
                    CApiBuiltinDesc va = existing.get();
                    // check the target: it needs to have the same signature, but with VA_LIST
                    ArgDescriptor[] argMod = function.arguments.clone();
                    argMod[argMod.length - 1] = ArgDescriptor.VA_LIST;
                    compareFunction(name, function.returnType, va.returnType, argMod, va.arguments);
                    forwards.put(function, name);
                } else {
                    if (function.call != NotImplemented && !MANUAL_VARARGS.contains(function.name)) {
                        missingVarargForwards.add(function.name);
                    }
                }
            } else {
                function.generateC(lines);
                toBeResolved.add(function);
            }
        }

        // insert varargs forwards at the end (targets may not be defined in header files)
        forwards.forEach((k, v) -> k.generateVarargForward(lines, v));
        if (!missingVarargForwards.isEmpty()) {
            System.out.println("""
                            Missing forwards for VARARG functions ('...' cannot cross NFI boundary,
                            so these functions either need to be implemented manually in capi_native.c,
                            or they need to use CApiBuiltin.forwardsTo to call a "Va"-style builtin)
                            """);
            System.out.println("    " + missingVarargForwards.stream().collect(Collectors.joining(", ")));
        }

        lines.add("void initializeCAPIForwards(void* (*getAPI)(const char*)) {");
        for (CApiBuiltinDesc function2 : toBeResolved) {
            lines.add("    " + function2.targetName() + " = getAPI(\"" + function2.name + "\");");
        }
        lines.add("}");

        return !missingVarargForwards.isEmpty() | writeGenerated(Path.of("com.oracle.graal.python.jni", "src", "capi_forwards.h"), lines);
    }

    /**
     * Generates the functions in capi.c that forward {@link CApiCallPath#Direct} builtins to their
     * associated Java implementations.
     */
    private static boolean generateCApiSource(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (var entry : javaBuiltins) {
            String name = entry.name;
            CApiBuiltinDesc value = entry;
            if (value.call == Direct) {
                lines.add("#undef " + name);
                String line = "PyAPI_FUNC(" + value.returnType.cSignature + ") " + name + "(";
                for (int i = 0; i < value.arguments.length; i++) {
                    line += (i == 0 ? "" : ", ") + CApiBuiltinDesc.getArgSignatureWithName(value.arguments[i], i);
                }
                line += ") {";
                lines.add(line);
                line = "    " + (value.returnType == ArgDescriptor.Void ? "" : "return ") + "Graal" + name + "(";
                for (int i = 0; i < value.arguments.length; i++) {
                    line += (i == 0 ? "" : ", ");
                    if (value.arguments[i] == ConstCharPtrAsTruffleString) {
                        line += "truffleString(" + argName(i) + ")";
                    } else {
                        line += argName(i);
                    }
                }
                line += ");";
                lines.add(line);
                lines.add("}");
            }
        }

        return writeGenerated(Path.of("com.oracle.graal.python.cext", "src", "capi.c"), lines);
    }

    /**
     * Generates the builtin specification in capi.h, which includes only the builtins implmemented
     * in Java code. Additionally, it generates helpers for all "Py_get_" and "Py_set_" builtins.
     */
    private static boolean generateCApiHeader(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#define CAPI_BUILTINS \\");
        int id = 0;
        for (var entry : javaBuiltins) {
            assert (id++) == entry.id;
            String line = "    BUILTIN(" + entry.name + ", " + entry.returnType.cSignature;
            for (var arg : entry.arguments) {
                line += ", " + arg.cSignature;
            }
            line += ") \\";
            lines.add(line);
        }
        lines.add("");

        for (var entry : javaBuiltins) {
            String name = entry.name;
            if (!name.endsWith("_dummy")) {
                if (name.startsWith("Py_get_")) {
                    assert entry.arguments.length == 1;
                    String type = entry.arguments[0].name().replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_';
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define " + name.substring(7) + "(OBJ) ( points_to_py_handle_space(OBJ) ? Graal" + name + "((" + type + "*) (OBJ)) : ((" + type + "*) (OBJ))->" + field + " )");
                    lines.add(macro.toString());
                } else if (name.startsWith("Py_set_")) {
                    assert entry.arguments.length == 2;
                    String type = entry.arguments[0].name().replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_';
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define set_" + name.substring(7) + "(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) Graal" + name + "((" + type + "*) (OBJ), (VALUE)); else  ((" + type +
                                    "*) (OBJ))->" + field + " = (VALUE); }");
                    lines.add(macro.toString());
                }
            }
        }

        return writeGenerated(Path.of("com.oracle.graal.python.cext", "src", "capi.h"), lines);
    }

    /**
     * Generates the contents of the {@link PythonCextBuiltinRegistry} class: the list of builtins,
     * the {@link CApiBuiltinNode} factory function, and the slot query function.
     */
    private static boolean generateBuiltinRegistry(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();

        lines.add("    public static final CApiBuiltinExecutable[] builtins = {");
        for (var builtin : javaBuiltins) {
            lines.add("                    new CApiBuiltinExecutable(\"" + builtin.name + "\", " + builtin.call + ", " + builtin.returnType + ",");
            String argString = Arrays.stream(builtin.arguments).map(Object::toString).collect(Collectors.joining(", "));
            lines.add("                                    new ArgDescriptor[]{" + argString + "}, " + builtin.id + "),");
        }
        lines.add("    };");
        lines.add("");

        lines.add("    static CApiBuiltinNode createBuiltinNode(int id) {");
        lines.add("        switch (id) {");

        for (var builtin : javaBuiltins) {
            lines.add("            case " + builtin.id + ":");
            lines.add("                return " + builtin.factory + ".create();");
        }

        lines.add("        }");
        lines.add("        return null;");
        lines.add("    }");
        lines.add("");
        lines.add("    public static CApiBuiltinExecutable getSlot(String key) {");
        lines.add("        switch (key) {");

        for (var builtin : javaBuiltins) {
            if (builtin.name.startsWith("Py_get_")) {
                lines.add("            case \"" + builtin.name.substring(7) + "\":");
                lines.add("                return builtins[" + builtin.id + "];");
            }
        }

        lines.add("        }");
        lines.add("        return null;");
        lines.add("    }");

        return writeGenerated(Path.of("com.oracle.graal.python", "src", "com", "oracle", "graal", "python", "builtins", "modules", "cext", "PythonCextBuiltinRegistry.java"), lines);
    }

    /**
     * Entry point for the "mx python-capi-forwards" command.
     */
    public static void main(String[] args) throws IOException {
        List<CApiBuiltinDesc> javaBuiltins = CApiFunction.getJavaBuiltinDefinitions();
        List<CApiBuiltinDesc> additionalBuiltins = CApiFunction.getOtherBuiltinDefinitions();

        List<CApiBuiltinDesc> allBuiltins = new ArrayList<>();
        allBuiltins.addAll(additionalBuiltins);
        for (var entry : javaBuiltins) {
            Optional<CApiBuiltinDesc> existing1 = findBuiltin(allBuiltins, entry.name);
            if (existing1.isPresent()) {
                compareFunction(entry.name, entry.returnType, existing1.get().returnType, entry.arguments, existing1.get().arguments);
            } else {
                allBuiltins.add(entry);
            }
        }
        Collections.sort(allBuiltins, (a, b) -> a.name.compareTo(b.name));

        boolean changed = false;
        changed |= generateCForwards(allBuiltins);
        changed |= generateCApiSource(javaBuiltins);
        changed |= generateCApiHeader(javaBuiltins);
        changed |= generateBuiltinRegistry(javaBuiltins);
        changed |= checkImports(allBuiltins);
        if (changed) {
            System.exit(-1);
        }
    }

    /**
     * Checks whether the "not implemented" state of builtins matches whether they exist in the capi
     * library: {@link CApiCallPath#NotImplemented} and {@link CApiCallPath#Ignored} builtins cannot
     * have an implementation, and all others need to be present.
     */
    public static boolean assertBuiltins(Object capiLibrary) {
        List<CApiBuiltinDesc> builtins = new ArrayList<>();
        builtins.addAll(CApiFunction.getOtherBuiltinDefinitions());
        builtins.addAll(CApiFunction.getJavaBuiltinDefinitions());

        TreeSet<String> messages = new TreeSet<>();
        for (CApiBuiltinDesc function : builtins) {
            if (InteropLibrary.getUncached().isMemberInvocable(capiLibrary, function.name)) {
                if (function.call == CImpl || function.call == CApiCallPath.PolyglotImpl || function.call == CApiCallPath.Direct) {
                    // ok
                } else {
                    messages.add("unexpected C impl: " + function.name);
                }
            } else {
                if (function.call == NotImplemented || function.call == Ignored) {
                    // ok
                } else {
                    messages.add("missing implementation: " + function.name);
                }
            }
        }

        messages.forEach(System.out::println);
        return messages.isEmpty();
    }

    private static final HashSet<String> OUTSIDE = new HashSet<>(
                    Arrays.asList("Py_DecRef", "Py_IncRef", "PyTuple_Pack", "PyArg_UnpackTuple", "PyOS_snprintf", "PyErr_WarnFormat", "PyCapsule_TypeReference", "_Py_TrueStructReference",
                                    "_Py_REFCNT", "_Py_SET_REFCNT", "_Py_NoneStructReference", "_Py_FalseStructReference", "Py_OptimizeFlag", "PyUnicode_FromFormat",
                                    "PyThreadState_Get", "PyErr_Format", "_Py_BuildValue_SizeT", "_PyObject_GetDictPtr", "_PyObject_CallFunction_SizeT",
                                    "PyObject_CallFunctionObjArgs", "_PyByteArray_empty_string", "_Py_tracemalloc_config"));

    /**
     * Check the list of implemented and unimplemented builtins against the list of CPython exported
     * symbols, to determine if builtins are missing. If a builtin is missing, this function
     * suggests the appropriate {@link CApiBuiltin} specification.
     */
    private static boolean checkImports(List<CApiBuiltinDesc> builtins) throws IOException {
        boolean result = false;
        List<String> lines = Files.readAllLines(resolvePath(Path.of("com.oracle.graal.python.cext", "CAPIFunctions.txt")));

        TreeSet<String> newBuiltins = new TreeSet<>();
        TreeSet<String> names = new TreeSet<>();
        builtins.forEach(n -> names.add(n.name));

        for (String line : lines) {
            String[] s = line.split(";");
            String name = s[0].trim();
            names.remove(name);
            ArgDescriptor ret = ArgDescriptor.resolve(s[1].trim());
            String[] argSplit = s[2].isBlank() || "void".equals(s[2]) ? new String[0] : s[2].trim().split("\\|");
            ArgDescriptor[] args = Arrays.stream(argSplit).map(ArgDescriptor::resolve).toArray(ArgDescriptor[]::new);

            if (!name.endsWith("_Type") && !name.startsWith("PyExc_") && !OUTSIDE.contains(name)) {
                Optional<CApiBuiltinDesc> existing = findBuiltin(builtins, name);
                if (existing.isPresent()) {
                    compareFunction(name, existing.get().returnType, ret, existing.get().arguments, args);
                } else {
                    String argString = Arrays.stream(args).map(t -> String.valueOf(t)).collect(Collectors.joining(", "));
                    newBuiltins.add("    @CApiBuiltin(name = \"" + name + "\", ret = " + ret + ", args = {" + argString + "}, call = NotImplemented)");
                }
            }
        }
        if (!newBuiltins.isEmpty()) {
            System.out.println("missing builtins (defined in CPython, but not in GraalPy):");
            newBuiltins.stream().forEach(System.out::println);
            result = true;
        }

        names.removeIf(n -> n.startsWith("Py_get_"));
        names.removeIf(n -> n.startsWith("Py_set_"));
        names.removeIf(n -> n.startsWith("PyTruffle"));
        names.removeIf(n -> n.startsWith("_PyTruffle"));
        System.out.println("extra builtins (defined in GraalPy, but not in CPython - some of these are necessary for internal modules like 'math'):");
        System.out.println("    " + names.stream().collect(Collectors.joining(", ")));
        return result;
    }
}
