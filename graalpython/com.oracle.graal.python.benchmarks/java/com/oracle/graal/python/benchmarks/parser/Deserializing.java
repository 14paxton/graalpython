/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.benchmarks.parser;

import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.parser.PythonSSTNodeFactory;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import java.util.ArrayList;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

public class Deserializing extends ParserBenchRunner {

    private static class Item {
        final byte[] data;

        public Item(byte[] serialization) {
            this.data = serialization;
        }
    }

    private List<Item> serializations;

    @Setup
    public void setup() {
        System.out.println("### setup ...");
        System.out.println("    Found " + getSources().size() + " Python sources");
        serializations = createSerializations();
        System.out.println("    Obtained " + serializations.size() + " serialization data");
    }

    @Benchmark
    public void execute(Blackhole bh) {
        for (int n = 0; n < parsingCycles; n++) {
            for (Item serialization : serializations) {
                bh.consume(parser.deserialize(core, serialization.data));
            }
        }
    }

    private List<Item> createSerializations() {
        List<Item> result = new ArrayList<>(getSources().size());

        for (Source source : getSources()) {
            try {
                PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(core, source, parser);
                PythonParserImpl.CacheItem item = parser.parseWithANTLR(PythonParser.ParserMode.File, 0, core, sstFactory, source, null, null);
                SourceSection section = source.createSection(0, source.getLength());
                result.add(new Item(PythonParserImpl.serialize(section, item.getAntlrResult(), item.getGlobalScope(), true)));
            } catch (RuntimeException e) {
                // do nothing
            }
        }
        return result;
    }

}
