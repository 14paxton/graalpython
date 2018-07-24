# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


def a_function():
    pass


def wrapper():
    values = []

    def my_func(arg_l, kwarg_case="empty set", kwarg_other=19):
        loc_1 = set(values)
        loc_2 = set(values)
        loc_3 = "set()"

        def inner_func():
            return kwarg_other + loc_2

        try:
            loc_1 &= kwarg_other
        except TypeError:
            pass
        else:
            print("expected TypeError")

    return my_func


def test_name():
    assert a_function.__code__.co_name == "a_function"


def test_filename():
    assert a_function.__code__.co_filename.rpartition("/")[2] == "test_code.py"


def test_firstlineno():
    assert a_function.__code__.co_firstlineno == 41


def test_code_attributes():
    code = wrapper().__code__
    assert code.co_argcount == 3
    assert code.co_kwonlyargcount == 0
    assert code.co_nlocals == 6
    assert code.co_stacksize >= code.co_nlocals
    assert code.co_flags == 0
    # assert code.co_code
    # assert code.co_consts
    # assert set(code.co_names) == {'set', 'TypeError', 'print'}
    assert set(code.co_varnames) == {'arg_l', 'kwarg_case', 'kwarg_other', 'loc_1', 'loc_3', 'inner_func'}
    assert code.co_filename == "graalpython/com.oracle.graal.python.test/src/tests/test_code.py"
    assert code.co_name == "my_func"
    assert code.co_firstlineno == 48
    # assert code.co_lnotab == b'\x00\x01\x0c\x01\x0c\x01\x06\x02\x15\x03\x03\x01\x0e\x01\r\x01\x05\x02'
    assert set(code.co_freevars) == {'values'}
    assert set(code.co_cellvars) == {'kwarg_other', 'loc_2'}
