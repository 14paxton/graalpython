# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

# Package context -- the full module name for package imports
_py_package_context = None


@__builtin__
def extension_suffixes():
    return [".bc", ".so", ".dylib", ".su"]


@__builtin__
def get_magic():
    return b'\x0c\xaf\xaf\xe1'


@__builtin__
def create_dynamic(module_spec, filename=None):
    global _py_package_context
    old_package_context = _py_package_context
    _py_package_context = str(module_spec.name)
    try:
        return __create_dynamic__(module_spec, filename)
    finally:
        _py_package_context = old_package_context


@__builtin__
def exec_builtin(mod):
    return None


@__builtin__
def init_frozen(name):
    return None


@__builtin__
def is_frozen(name):
    return False


@__builtin__
def get_frozen_object(name):
    raise ImportError("No such frozen object named %s" % name)


is_frozen_package = get_frozen_object


@__builtin__
def freeze_module(mod, key=None):
    """
    Freeze a module under the optional key in the language cache so that it can
    be shared across multiple contexts.
    """
    is_package = hasattr(mod, "__path__")
    name = key or mod.__name__
    graal_python_cache_module_code(key, mod.__file__, is_package)


@__builtin__
def cache_all_file_modules():
    """
    Caches all modules loaded during initialization through the normal import
    mechanism on the language, so that any additional contexts created in the
    same engine can re-use the cached CallTargets. See the _imp module for
    details on the module caching.
    """
    import sys
    for k,v in sys.modules.items():
        if hasattr(v, "__file__"):
            if not graal_python_has_cached_code(k):
                freeze_module(v, k)


class CachedLoader:
    import sys

    @staticmethod
    def create_module(spec):
        pass

    @staticmethod
    def exec_module(module):
        modulename = module.__name__
        exec(graal_python_get_cached_code(modulename), module.__dict__)
        CachedLoader.sys.modules[modulename] = module


class CachedImportFinder:
    import sys

    @staticmethod
    def find_spec(fullname, path, target=None):
        from _frozen_importlib import ModuleSpec
        is_package = graal_python_cached_code_is_package(fullname)
        sys = CachedImportFinder.sys
        if is_package is not None:
            spec = ModuleSpec(fullname, CachedLoader, is_package=is_package)
            folder = sys.graal_python_stdlib_home + "/" + fullname.replace(".", "/")
            if is_package:
                origin = folder + "/__init__.py"
            else:
                origin = folder + ".py"
            spec.origin = origin
            spec.submodule_search_locations = [folder]
            return spec
