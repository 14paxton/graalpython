# Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import os
import sys, ast, unittest
import inspect


@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
def test_guard():
    def f(x, g):
        match x:
            case x if g == 1:
                return 42
            case _:
                return 0

    assert f(1, 1) == 42
    assert f(1, 2) == 0

    def f(x):
        match x:
            case x as g if g == 1:
                return 42
            case _:
                return 0

    assert f(1) == 42
    assert f(2) == 0

@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
def test_complex_as_binary_op():
    src = """
def f(a):
    match a:
        case 2+3j:
            return "match add"
        case 2-3j:
            return "match sub"
    return "no match"
"""

    tree = ast.parse(src)
    # replace "case 2+3j" with "case 2+(4+3j)"
    tree.body[0].body[0].cases[0].pattern.value.right = ast.Constant(4+3j)
    # replace "case 2-3j" with "case 2-(4+3j)"
    tree.body[0].body[0].cases[1].pattern.value.right = ast.Constant(4+3j)
    ast.fix_missing_locations(tree)
    vars = {}
    exec(compile(tree, "<string>", "exec"), vars)
    f = vars['f']

    assert f(6+3j) == "match add"
    assert f(-2-3j) == "match sub"

@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
@unittest.skipIf(os.environ.get('BYTECODE_DSL_INTERPRETER'), "TODO: mapping pattern matching")
def test_long_mapping():
    def f(x):
        match d:
            case {0:0, 1:1, 2:2, 3:3, 4:4, 5:5, 6:6, 7:7, 8:8, 9:9, 10:10, 11:11, 12:12, 13:13, 14:14, 15:15, 16:16, 17:17, 18:18, 19:19, 20:20, 21:21, 22:22, 23:23, 24:24, 25:25, 26:26, 27:27, 28:28, 29:29, 30:30, 31:31, 32:32, 33:33}:
                return 42
        return 0

    d = {0:0, 1:1, 2:2, 3:3, 4:4, 5:5, 6:6, 7:7, 8:8, 9:9, 10:10, 11:11, 12:12, 13:13, 14:14, 15:15, 16:16, 17:17, 18:18, 19:19, 20:20, 21:21, 22:22, 23:23, 24:24, 25:25, 26:26, 27:27, 28:28, 29:29, 30:30, 31:31, 32:32, 33:33}
    assert f(d) == 42

    def star_match(x):
        match d:
            case {0:0, 1:1, 2:2, 3:3, 4:4, 5:5, 6:6, 7:7, 8:8, 9:9, 10:10, 11:11, 12:12, 13:13, 14:14, 15:15, 16:16, 17:17, 18:18, 19:19, 20:20, 21:21, 22:22, 23:23, 24:24, 25:25, 26:26, 27:27, 28:28, 29:29, 30:30, 31:31, 32:32, **z}:
                return z
        return 0

    assert star_match(d) == {33:33}

@unittest.skipIf(os.environ.get('BYTECODE_DSL_INTERPRETER'), "TODO: mapping pattern matching")
def test_mutable_dict_keys():
    class MyObj:
        pass

    def forward(**kwargs):
        return kwargs

    def test(name):
        to_match = {'attr1': 1, 'attr2': 2, 'attr3': 3}
        x = MyObj()
        x.myattr = name
        match to_match:
            case {x.myattr: dyn_match, **data}:
                return forward(dyn_match=dyn_match, **data)

    assert test('attr1') == {'dyn_match': 1, 'attr2': 2, 'attr3': 3}
    assert test('attr2') == {'dyn_match': 2, 'attr1': 1, 'attr3': 3}

@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
def test_multiple_or_pattern_basic():
    match 0:
        case 0 | 1 | 2 | 3 | 4 | 5 as x:
            assert x == 0

    match 3:
        case ((0 | 1 | 2) as x) | ((3 | 4 | 5) as x):
            assert x == 3

@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
def test_sequence_pattern():
    match (1, 2):
        case (3, 2):
            assert False

    match (1, (2, 2)):
        case (3, (2, 2)):
            assert False

    match (1, 2):
        case (3, q):
            assert False


@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
def test_multiple_or_pattern_advanced():
    match 4:
        case (0 as z) | (1 as z) | (2 as z) | (4 as z) | (77 as z):
            assert z == 4

    match 42:
        case (0 as z) | (1 as z):
            assert z == 1
        case x:
            assert x == 42

    match 2:
        case (0 as z) | (1 as z) | (2 as z):
            assert z == 2
        case _:
            assert False

    match 1:
        case (0 as z) | (1 as z) | (2 as z):
            assert z == 1
        case _:
            assert False

    match 0:
        case (0 as z) | (1 as z) | (2 as z):
            assert z == 0
        case _:
            assert False

    match (1, 2):
        case (w, 2) | (2, w):
            assert w == 1


@unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
def test_multiple_or_pattern_creates_locals():
    match (1, 2):
        case (a, 1) | (a, 2):
            assert a == 1
    assert a == 1

    match [1, 2]:
        case [a1, 1] | [a1, 2]:
            assert a1 == 1
    assert a1 == 1

    match (1, 2, 2, 3, 2):
        case (1, a, b, 4, c) | (1, a, b, 3, c) | (1, a, b, 2, c):
            assert a == 2
            assert b == 2
            assert c == 2
    assert a == 2
    assert b == 2
    assert c == 2

    match (1, 3, 4, 9):
        case ((d, e, f, 7) | (d, e, f, 8) | (d, e, f, 6) | (d, e, f, 9)):
            assert d == 1
            assert e == 3
            assert f == 4
    assert d == 1
    assert e == 3
    assert f == 4

    match (1,2,3,4,5,6,7):
        case (0,q,w,e,r,t,y) | (q,w,e,r,t,y,7):
            assert q == 1
            assert w == 2
            assert e == 3
            assert r == 4
            assert t == 5
            assert y == 6
    assert q == 1
    assert w == 2
    assert e == 3
    assert r == 4
    assert t == 5
    assert y == 6


class TestOriginalPatMa(unittest.TestCase):
    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_patma_246(self):
        def f(x):
            match x:
                case (
                (h, g, i, a, b, d, e, c, f, 10) |
                (a, b, c, d, e, f, g, h, i, 9) |
                (g, b, a, c, d, -5, e, h, i, f) |
                (-1, d, f, b, g, e, i, a, h, c)
                ):
                    w = 0
            out = locals()
            del out["x"]
            return out
        alts = [
            dict(a=0, b=1, c=2, d=3, e=4, f=5, g=6, h=7, i=8, w=0),
            dict(h=1, g=2, i=3, a=4, b=5, d=6, e=7, c=8, f=9, w=0),
            dict(g=0, b=-1, a=-2, c=-3, d=-4, e=-6, h=-7, i=-8, f=-9, w=0),
            dict(d=-2, f=-3, b=-4, g=-5, e=-6, i=-7, a=-8, h=-9, c=-10, w=0),
            dict(),
        ]
        self.assertEqual(f(range(10)), alts[0])
        self.assertEqual(f(range(1, 11)), alts[1])
        self.assertEqual(f(range(0, -10, -1)), alts[2])
        self.assertEqual(f(range(-1, -11, -1)), alts[3])
        self.assertEqual(f(range(10, 20)), alts[4])

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_patma_242(self):
        x = range(3)
        match x:
            case [y, *_, z]:
                w = 0
        self.assertEqual(w, 0)
        self.assertEqual(x, range(3))
        self.assertEqual(y, 0)
        self.assertEqual(z, 2)

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_patma_017(self):
        match (0, 1, 2):
            case [*x, 0, 1, 2,]:
                y = 0
        self.assertEqual(x, [])
        self.assertEqual(y, 0)


class TestErrors(unittest.TestCase):
    def assert_syntax_error(self, code: str):
        with self.assertRaises(SyntaxError):
            compile(inspect.cleandoc(code), "<test>", "exec")

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_alternative_patterns_bind_different_names_0(self):
        self.assert_syntax_error("""
            match ...:
                case "a" | a:
                    pass
            """)

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_alternative_patterns_bind_different_names_1(self):
        self.assert_syntax_error("""
        match ...:
            case [a, [b] | [c] | [d]]:
                pass
        """)

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_multiple_or_same_name(self):
        self.assert_syntax_error("""
        match 0:
            case x | x:
                pass
        """)

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_multiple_or_wildcard(self):
        self.assert_syntax_error("""
        match 0:
            case * | 1:
                pass
        """)

    @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
    def test_unbound_local_variable(self):
        with self.assertRaises(UnboundLocalError):
            match (1, 3):
                case (a, 1) | (a, 2):
                    pass
            assert a == 1