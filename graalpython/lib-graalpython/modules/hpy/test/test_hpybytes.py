# MIT License
# 
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from .support import HPyTest

class TestBytes(HPyTest):

    def test_Check(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                if (HPyBytes_Check(ctx, arg))
                    return HPy_Dup(ctx, ctx->h_True);
                return HPy_Dup(ctx, ctx->h_False);
            }
            @EXPORT(f)
            @INIT
        """)
        class MyBytes(bytes):
            pass

        assert mod.f(b'hello') is True
        assert mod.f('hello') is False
        assert mod.f(MyBytes(b'hello')) is True

    def test_Size(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                HPy_ssize_t a = HPyBytes_Size(ctx, arg);
                HPy_ssize_t b = HPyBytes_GET_SIZE(ctx, arg);
                return HPyLong_FromLong(ctx, 10*a + b);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(b'hello') == 55

    def test_AsString(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                long res = 0;
                HPy_ssize_t n = HPyBytes_Size(ctx, arg);
                char *buf = HPyBytes_AsString(ctx, arg);
                for(int i=0; i<n; i++)
                    res = (res * 10) + buf[i];
                return HPyLong_FromLong(ctx, res);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(b'ABC') == 100*ord('A') + 10*ord('B') + ord('C')

    def test_AS_STRING(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                long res = 0;
                HPy_ssize_t n = HPyBytes_Size(ctx, arg);
                char *buf = HPyBytes_AS_STRING(ctx, arg);
                for(int i=0; i<n; i++)
                    res = (res * 10) + buf[i];
                return HPyLong_FromLong(ctx, res);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(b'ABC') == 100*ord('A') + 10*ord('B') + ord('C')
