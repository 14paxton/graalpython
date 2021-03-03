/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic({NativeMember.class, PythonOptions.class})
public abstract class PyDateTimeMRNode extends Node {

    public enum DateTimeMode {
        DATE,
        TIME,
        DATE_TIME
    }

    static DateTimeMode getModeFromTypeName(String typeName) {
        if ("date".equals(typeName)) {
            return DateTimeMode.DATE;
        } else if ("datetime".equals(typeName)) {
            return DateTimeMode.DATE_TIME;
        } else if ("time".equals(typeName)) {
            return DateTimeMode.TIME;
        }
        return null;
    }

    public abstract Object execute(PythonObject object, String key, DateTimeMode mode);

    public static final String YEAR = "year";
    public static final String MONTH = "month";
    public static final String DAY = "day";
    public static final String HOUR = "hour";
    public static final String MIN = "minute";
    public static final String SEC = "second";
    public static final String USEC = "microsecond";
    public static final String TZINFO = "tzinfo";

    /**
     * Fields are packed into successive bytes, each viewed as unsigned and big-endian, unless
     * otherwise noted:
     *
     * PyDateTime_DateTime:
     * 
     * <pre>
     * byte offset
     *  0           year     2 bytes, 1-9999
     *  2           month    1 byte, 1-12
     *  3           day      1 byte, 1-31
     *  4           hour     1 byte, 0-23
     *  5           minute   1 byte, 0-59
     *  6           second   1 byte, 0-59
     *  7           usecond  3 bytes, 0-999999
     * 10
     * </pre>
     *
     * PyDateTime_Date:
     * 
     * <pre>
     * byte offset
     *  0           year     2 bytes, 1-9999
     *  2           month    1 byte, 1-12
     *  3           day      1 byte, 1-31
     * 10
     * </pre>
     * 
     * PyDateTime_Time:
     * 
     * <pre>
     * byte offset
     *  4           hour     1 byte, 0-23
     *  5           minute   1 byte, 0-59
     *  6           second   1 byte, 0-59
     *  7           usecond  3 bytes, 0-999999
     * 10
     * </pre>
     */
    @Specialization(guards = {"eq(DATETIME_DATA, key)", "cachedMode == mode", "cachedMode != null"}, limit = "1")
    static Object doData(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") DateTimeMode mode,
                    @Cached("mode") DateTimeMode cachedMode,
                    @Cached PInteropGetAttributeNode getYearNode,
                    @Cached PInteropGetAttributeNode getMonthNode,
                    @Cached PInteropGetAttributeNode getDayNode,
                    @Cached PInteropGetAttributeNode getHourNode,
                    @Cached PInteropGetAttributeNode getMinNode,
                    @Cached PInteropGetAttributeNode getSecNode,
                    @Cached PInteropGetAttributeNode getUSecNode,
                    @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {

        // passing null here should be ok, since we should be in an interop situation
        int year = -1;
        int month = -1;
        int day = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        int usec = -1;
        if (cachedMode == DateTimeMode.DATE || cachedMode == DateTimeMode.DATE_TIME) {
            year = lib.asSize(getYearNode.execute(object, YEAR));
            month = lib.asSize(getMonthNode.execute(object, MONTH));
            day = lib.asSize(getDayNode.execute(object, DAY));
            assert year >= 0 && year < 0x10000;
            assert month >= 0 && month < 0x100;
            assert day >= 0 && day < 0x100;
        }
        if (cachedMode == DateTimeMode.TIME || cachedMode == DateTimeMode.DATE_TIME) {
            hour = lib.asSize(getHourNode.execute(object, HOUR));
            min = lib.asSize(getMinNode.execute(object, MIN));
            sec = lib.asSize(getSecNode.execute(object, SEC));
            usec = lib.asSize(getUSecNode.execute(object, USEC));
            assert hour >= 0 && hour < 0x100;
            assert min >= 0 && min < 0x100;
            assert sec >= 0 && sec < 0x100;
            assert usec >= 0 && sec < 0x1000000;
        }

        byte[] data;
        switch (cachedMode) {
            case DATE:
                data = new byte[]{(byte) (year >> 8), (byte) year, (byte) month, (byte) day};
                break;
            case TIME:
                data = new byte[]{(byte) hour, (byte) min, (byte) sec, (byte) (usec >> 16), (byte) (usec >> 8), (byte) usec};
                break;
            case DATE_TIME:
                data = new byte[]{(byte) (year >> 8), (byte) year, (byte) month, (byte) day, (byte) hour, (byte) min, (byte) sec, (byte) (usec >> 16), (byte) (usec >> 8), (byte) usec};
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        return new CByteArrayWrapper(data);
    }

    @Specialization(guards = "eq(DATETIME_TZINFO, key)")
    static Object doTzinfo(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") DateTimeMode mode,
                    @Cached ReadAttributeFromObjectNode getTzinfoNode,
                    @Cached ToSulongNode toSulongNode) {
        Object value = getTzinfoNode.execute(object, TZINFO);
        if (value != PNone.NO_VALUE) {
            return toSulongNode.execute(value);
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    protected static boolean eq(NativeMember expected, String actual) {
        return expected.getMemberName().equals(actual);
    }
}
