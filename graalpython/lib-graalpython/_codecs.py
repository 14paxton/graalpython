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


__codec_search_path__ = []
__codec_search_cache__ = {}
__codec_error_registry__ = {}


def register(search_function):
    if not __codec_search_path__:
        __codec_registry_init__()

    if not hasattr(search_function, "__call__"):
        raise TypeError("argument must be callable")
    __codec_search_path__.append(search_function)


def __normalizestring(string):
    return string.replace(' ', '-').lower()


def lookup(encoding):
    if not __codec_search_path__:
        __codec_registry_init__()

    normalized_encoding = __normalizestring(encoding)
    # First, try to lookup the name in the registry dictionary
    result = __codec_search_cache__.get(normalized_encoding)
    if result:
        return result

    # Next, scan the search functions in order of registration
    for func in __codec_search_path__:
        result = func(normalized_encoding)
        if result:
            if not (isinstance(result, tuple) and len(result) == 4):
                raise TypeError("codec search functions must return 4-tuples %r")
            break

    if result:
        # Cache and return the result
        __codec_search_cache__[normalized_encoding] = result
        return result

    raise LookupError("unknown encoding: %s" % encoding)


def _forget_codec(encoding):
    normalized_encoding = __normalizestring(encoding)
    return __codec_search_cache__.pop(normalized_encoding)


def __codec_getitem(encoding, index):
    return lookup(encoding)[index]


def __encoder(encoding):
    return __codec_getitem(encoding, 0)


def __decoder(encoding):
    return __codec_getitem(encoding, 1)


def encode(obj, encoding='utf-8', errors='strict'):
    encoder = __encoder(encoding)
    if encoder:
        result = encoder(obj, errors)
        if not (isinstance(result, tuple) and len(result) == 2):
            raise TypeError('encoder must return a tuple (object, integer)')
        return result[0]


def decode(obj, encoding='utf-8', errors='strict'):
    decoder = __decoder(encoding)
    if decoder:
        result = decoder(obj, errors)
        if not (isinstance(result, tuple) and len(result) == 2):
            raise TypeError('decoder must return a tuple (object, integer)')
        return result[0]


def register_error(errors, handler):
    if not hasattr(handler, '__call__'):
        raise TypeError('handler must be callable')
    __codec_error_registry__[errors] = handler


def lookup_error(errors='strict'):
    handler = __codec_error_registry__.get(errors)
    if handler is None:
        raise LookupError('unknown error handler name %s'.format(errors))
    return handler


def __codec_registry_init__():
    # TODO: error method handlers setup
    try:
        import encodings
    except Exception:
        raise RuntimeError("can't initialize codec registry")


# TODO implement the encode / decode methods
@staticmethod
def escape_encode(data, errors=None):
    raise NotImplementedError()


@staticmethod
def escape_decode(data, errors=None):
    raise NotImplementedError()


@staticmethod
def utf_8_encode(string, errors=None):
    return __truffle_encode(string, "utf-8", errors)


@staticmethod
def utf_8_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-8", errors)


@staticmethod
def utf_7_encode(string, errors=None):
    return __truffle_encode(string, "utf-7", errors)


@staticmethod
def utf_7_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-7", errors)


@staticmethod
def utf_16_encode(string, errors=None, byteorder=0):
    return __truffle_encode(string, "utf-16", errors)


@staticmethod
def utf_16_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-16", errors)


@staticmethod
def utf_16_le_encode(string, errors=None):
    return __truffle_encode(string, "utf-16-le", errors)


@staticmethod
def utf_16_le_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-16-le", errors)


@staticmethod
def utf_16_be_encode(string, errors=None):
    return __truffle_encode(string, "utf-16-be", errors)


@staticmethod
def utf_16_be_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-16-be", errors)


@staticmethod
def utf_16_ex_decode(data, errors=None, byteorder=0, final=False):
    raise NotImplementedError()


@staticmethod
def utf_32_encode(string, errors=None, byteorder=0):
    return __truffle_encode(string, "utf-32", errors)


@staticmethod
def utf_32_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-32", errors)


@staticmethod
def utf_32_le_encode(string, errors=None):
    return __truffle_encode(string, "utf-32-le", errors)


@staticmethod
def utf_32_le_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-32-le", errors)


@staticmethod
def utf_32_be_encode(string, errors=None):
    return __truffle_encode(string, "utf-32-be", errors)


@staticmethod
def utf_32_be_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-32-be", errors)


@staticmethod
def utf_32_ex_decode(data, errors=None, byteorder=0, final=False):
    raise NotImplementedError()


@staticmethod
def unicode_escape_encode(string, errors=None):
    raise NotImplementedError()


@staticmethod
def unicode_escape_decode(string, errors=None):
    raise NotImplementedError()


@staticmethod
def unicode_internal_encode(obj, errors=None):
    raise NotImplementedError()


@staticmethod
def unicode_internal_decode(obj, errors=None):
    raise NotImplementedError()


@staticmethod
def raw_unicode_escape_encode(string, errors=None):
    raise NotImplementedError()


@staticmethod
def raw_unicode_escape_decode(string, errors=None):
    raise NotImplementedError()


@staticmethod
def latin_1_encode(string, errors=None):
    return __truffle_encode(string, "latin-1", errors)


@staticmethod
def latin_1_decode(string, errors=None):
    return __truffle_decode(string, "latin-1", errors)


@staticmethod
def ascii_encode(string, errors=None):
    return __truffle_encode(string, "ascii", errors)


@staticmethod
def ascii_decode(string, errors=None):
    return __truffle_decode(string, "ascii", errors)


@staticmethod
def charmap_encode(string, errors=None, mapping=None):
    raise NotImplementedError()


@staticmethod
def charmap_decode(string, errors=None, mapping=None):
    raise NotImplementedError()


@staticmethod
def charmap_build(mapping):
    raise NotImplementedError()


@staticmethod
def readbuffer_encode(data, errors=None):
    raise NotImplementedError()


@staticmethod
def mbcs_encode(string, errors=None):
    raise NotImplementedError()


@staticmethod
def mbcs_decode(string, errors=None, final=False):
    raise NotImplementedError()


@staticmethod
def oem_encode(string, errors):
    raise NotImplementedError()


@staticmethod
def oem_decode(string, errors=None, final=False):
    raise NotImplementedError()


@staticmethod
def code_page_encode(code_page, string, errors=None):
    raise NotImplementedError()


@staticmethod
def code_page_decode(code_page, string, errors=None, final=False):
    raise NotImplementedError()
