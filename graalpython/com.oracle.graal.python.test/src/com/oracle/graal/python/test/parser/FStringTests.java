/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class FStringTests extends ParserTestBase {

    public FStringTests() {
        printFormatStringLiteralValues = true;
    }

    @Test
    public void twoStrings01() throws Exception {
        checkTreeResult("'123'  '456'");
    }

    @Test
    public void twoStrings02() throws Exception {
        checkTreeResult("f'123'  '456'");
    }

    @Test
    public void twoStrings03() throws Exception {
        checkTreeResult("'123'  f'456'");
    }

    @Test
    public void twoStrings04() throws Exception {
        checkTreeResult("f'123'  f'456'");
    }

    @Test
    public void funcDoc01() throws Exception {
        checkTreeResult("def fn(): '1234' f'567'; pass");
    }

    @Test
    public void funcDoc02() throws Exception {
        checkTreeResult("def fn(): '1234' '567'; pass");
    }

    @Test
    public void funcDoc03() throws Exception {
        checkTreeResult("def fn(): f'1234'; pass");
    }

    @Test
    public void funcDoc04() throws Exception {
        checkTreeResult("def fn(): '123' f'456' '789'; pass");
    }

    @Test
    public void topLevelParser01() throws Exception {
        checkTreeResult(
                        "name = 'Pepa'\n" +
                                        "print(f\"hello {name}\")");
    }

    @Test
    public void topLevelParser02() throws Exception {
        checkTreeResult(
                        "''.join(f'{name}' for name in ['Pepa', 'Pavel'])");
    }

    @Test
    public void moreValues01() throws Exception {
        checkTreeResult("'123' '456' f'789' '0'");
    }

    @Test
    public void moreValues02() throws Exception {
        checkTreeResult("'1' '2' '3' f'4' f'5' '6' '7' f'8' '9' '0'");
    }

}
