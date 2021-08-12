"""Provide access to Python's configuration information.
This is actually Graal.Python's minimal configuration information,
derived from PyPy's information (see sysconfig_pypy.py).

The specific configuration variables available depend heavily on the
platform and configuration.  The values may be retrieved using
get_config_var(name), and the list of variables is available via
get_config_vars().keys().  Additional convenience functions are also
available.
"""

import sys
import os
import _imp
import _sysconfig

from distutils.errors import DistutilsPlatformError


PREFIX = os.path.normpath(sys.prefix)
EXEC_PREFIX = os.path.normpath(sys.exec_prefix)
BASE_PREFIX = os.path.normpath(sys.base_prefix)
BASE_EXEC_PREFIX = os.path.normpath(sys.base_exec_prefix)
project_base = os.path.dirname(os.path.abspath(sys.executable))
python_build = False


def get_python_inc(plat_specific=0, prefix=None):
    return _sysconfig.get_python_inc()

def get_python_version():
    """Return a string containing the major and minor Python version,
    leaving off the patchlevel.  Sample return values could be '1.5'
    or '2.2'.
    """
    return _sysconfig.get_python_version()


def get_python_lib(plat_specific=0, standard_lib=0, prefix=None):
    """Return the directory containing the Python library (standard or
    site additions).

    If 'plat_specific' is true, return the directory containing
    platform-specific modules, i.e. any module from a non-pure-Python
    module distribution; otherwise, return the platform-shared library
    directory.  If 'standard_lib' is true, return the directory
    containing standard Python library modules; otherwise, return the
    directory for site-specific modules.

    If 'prefix' is supplied, use it instead of sys.prefix or
    sys.exec_prefix -- i.e., ignore 'plat_specific'.
    """
    if prefix is None:
        prefix = PREFIX
    # Keep in sync with sysconfig module, distutils.install and site module
    if standard_lib:
        return os.path.join(prefix, "lib-python", sys.version[0])
    return os.path.join(prefix, "lib-python", sys.version[0], "site-packages")


_config_vars = None

def _init_posix():
    """Initialize the module as appropriate for POSIX systems."""
    global _config_vars
    _config_vars = _sysconfig._get_posix_vars()


def _init_nt():
    """Initialize the module as appropriate for NT"""
    g = {}
    g['EXT_SUFFIX'] = _imp.extension_suffixes()[0]
    g['EXE'] = ".exe"
    g['SO'] = ".pyd"
    g['SOABI'] = g['SO'].rsplit('.')[0]   # xxx?

    global _config_vars
    _config_vars = g


def get_config_vars(*args):
    """With no arguments, return a dictionary of all configuration
    variables relevant for the current platform.  Generally this includes
    everything needed to build extensions and install both pure modules and
    extensions.  On Unix, this means every variable defined in Python's
    installed Makefile; on Windows and Mac OS it's a much smaller set.

    With arguments, return a list of values that result from looking up
    each argument in the configuration variable dictionary.
    """
    global _config_vars
    if _config_vars is None:
        func = globals().get("_init_" + os.name)
        if func:
            func()
        else:
            _config_vars = {}

        _config_vars['prefix'] = PREFIX
        _config_vars['exec_prefix'] = EXEC_PREFIX

    if args:
        vals = []
        for name in args:
            vals.append(_config_vars.get(name))
        return vals
    else:
        return _config_vars

def get_config_var(name):
    """Return the value of a single variable using the dictionary
    returned by 'get_config_vars()'.  Equivalent to
    get_config_vars().get(name)
    """
    return get_config_vars().get(name)


def customize_compiler(compiler):
    """Do any platform-specific customization of a CCompiler instance.

    Mainly needed on Unix, so we can plug in the information that
    varies across Unices and is stored in Python's Makefile.
    """
    if compiler.compiler_type == "unix":
        if sys.platform == "darwin":
            # Perform first-time customization of compiler-related
            # config vars on OS X now that we know we need a compiler.
            # This is primarily to support Pythons from binary
            # installers.  The kind and paths to build tools on
            # the user system may vary significantly from the system
            # that Python itself was built on.  Also the user OS
            # version and build tools may not support the same set
            # of CPU architectures for universal builds.
            global _config_vars
            # Use get_config_var() to ensure _config_vars is initialized.
            if not get_config_var('CUSTOMIZED_OSX_COMPILER'):
                import _osx_support
                _osx_support.customize_compiler(_config_vars)
                _config_vars['CUSTOMIZED_OSX_COMPILER'] = 'True'

        # TRUFFLE CHANGE BEGIN: added 'ranlib'
        (cc, cxx, opt, cflags, ccshared, ldshared, shlib_suffix, ar, ar_flags, ranlib) = \
            get_config_vars('CC', 'CXX', 'OPT', 'CFLAGS',
                            'CCSHARED', 'LDSHARED', 'SHLIB_SUFFIX', 'AR', 'ARFLAGS', 'RANLIB')
        # TRUFFLE CHANGE END

        if 'CC' in os.environ:
            newcc = os.environ['CC']
            if (sys.platform == 'darwin'
                    and 'LDSHARED' not in os.environ
                    and ldshared.startswith(cc)):
                # On OS X, if CC is overridden, use that as the default
                #       command for LDSHARED as well
                ldshared = newcc + ldshared[len(cc):]
            cc = newcc
        if 'CXX' in os.environ:
            cxx = os.environ['CXX']
        if 'LDSHARED' in os.environ:
            ldshared = os.environ['LDSHARED']
        if 'CPP' in os.environ:
            cpp = os.environ['CPP']
        else:
            cpp = cc + " -E"           # not always
        if 'LDFLAGS' in os.environ:
            ldshared = ldshared + ' ' + os.environ['LDFLAGS']
        if 'CFLAGS' in os.environ:
            cflags = opt + ' ' + os.environ['CFLAGS']
            ldshared = ldshared + ' ' + os.environ['CFLAGS']
        if 'CPPFLAGS' in os.environ:
            cpp = cpp + ' ' + os.environ['CPPFLAGS']
            cflags = cflags + ' ' + os.environ['CPPFLAGS']
            ldshared = ldshared + ' ' + os.environ['CPPFLAGS']
        if 'AR' in os.environ:
            ar = os.environ['AR']
        if 'ARFLAGS' in os.environ:
            archiver = ar + ' ' + os.environ['ARFLAGS']
        else:
            archiver = ar + ' ' + ar_flags
        # TRUFFLE CHANGE BEGIN: added 'ranlib'
        if compiler.executables['ranlib']:
            if 'RANLIB' in os.environ:
                ranlib = os.environ['RANLIB']
        else:
            ranlib = None
        # TRUFFLE CHANGE END

        cc_cmd = cc + ' ' + cflags
        compiler.set_executables(
            preprocessor=cpp,
            compiler=cc_cmd,
            compiler_so=cc_cmd + ' ' + ccshared,
            compiler_cxx=cxx,
            linker_so=ldshared,
            linker_exe=cc,
            archiver=archiver,
            # TRUFFLE CHANGE BEGIN: added 'ranlib'
            # Note: it will only be !=None if compiler already had a default indicating that it needs ranlib
            ranlib=ranlib
            # TRUFFLE CHANGE END
            )

        compiler.shared_lib_extension = shlib_suffix
