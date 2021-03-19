# Implementation Details

## Abstract Operations on Python Objects

Many generic operations on Python objects in CPython are defined in the header
files `object.h` and `abstract.h`. These operations are widely used and their
interplay and intricacies are the cause for the conversion, error message, and
control flow bugs when not mimicked correctly. Our current approach is to
provide many of these abstract operations as part of the `PythonObjectLibrary`.

### Common operations in the PythonObjectLibrary

The code has evolved over time, so not all built-in nodes are prime examples of
messages that should be used from the PythonObjectLibrary. We are refactoring
this as we go, but here are a few examples for things you can (or should soon be
able to) use the PythonObjectLibrary for:

 - casting and coercion to `java.lang.String`, array-sized Java `int`, Python
   index, fileno, `double`, filesystem path, iterator, and more
 - reading the class of an object
 - accessing the `__dict__` attribute of an object
 - hashing objects and testing for equality
 - testing for truthy-ness
 - getting the length
 - testing for abstract types such as `mapping`, `sequence`, `callable`
 - invoking methods or executing callables
 - access objects through the buffer protocol

### PythonObjectLibrary functions with and without state

Usually, there are at least two messages for each operation - one that takes a
`ThreadState` argument, and one that doesn't. The intent is to allow passing of
exception state and caller information similar to how we do it with the `PFrame`
argument even across library messages, which cannot take a VirtualFrame.

All nodes that are used in message implementations must allow uncached
usage. Often (e.g. in the case of the generic `CallNode`) they offer execute
methods with and without frames. If a `ThreadState` was passed to the message, a
frame to pass to the node can be reconstructed using
`PArguments.frameForCall(threadState)`. Here's an example:

```java
@ExportMessage
long messageWithState(ThreadState state,
        @Cached CallNode callNode) {
    Object callable = ...

    if (state != null) {
        return callNode.execute(PArguments.frameForCall(state), callable, arguments);
    } else {
        return callNode.execute(callable, arguments);
    }
}
```

*Note*: It is **always** preferable to call an `execute` method with a
`VirtualFrame` when both one with and without exist! The reason is that this
avoids materialization of the frame state in more cases, as described on the
section on Python's global thread state above.

### Other libraries in the codebase

Accessing hashing storages (the storage for `dict`, `set`, and `frozenset`)
should be done via the `HashingStorageLibrary`. We are in the process of
creating a `SequenceStorageLibrary` for sequence types (`tuple`, `list`) to
replace the `SequenceStorageNodes` collection of classes.

## Python Global Thread State

In CPython, each stack frame is allocated on the heap, and there's a global
thread state holding on to the chain of currently handled exceptions (e.g. if
you're nested inside `except:` blocks) as well as the currently flying exception
(e.g. we're just unwinding the stack).

In PyPy, this is done via their virtualizable frames and a global reference to
the current top frame. Each frame also has a "virtual reference" to its parent
frame, so code can just "force" these references to make the stack reachable if
necessary.

Unfortunately, the elegant solution of "virtual references" doesn't work for us,
mostly because we're not a tracing JIT: we want the reference to be "virtual"
even when there are multiple compilation units. With PyPy's solution, this also
isn't the case, but it only hurts them for nested loops when large stacks must
be forced to the heap.

In Graal Python, the implementation is thus a bit more involved. Here's how it
works.

### The PFrame.Reference

A `PFrame.Reference` is created when entering a Python function. By default it
only holds on to another reference, that of the Python caller. If there are
non-Python frames between the newly entered frame and the last Python frame,
those are ignored - our linked list only connects Python frames. The entry point
into the interpreter has a `PFrame.Reference` with no caller.

#### ExecutionContext.CallContext and ExecutionContext.CalleeContext

If we're only calling between Python, we pass our `PFrame.Reference` as implicit
argument to any callees. On entry, they will create their own `PFrame.Reference`
as the next link in this backwards-connected linked-list. As an optimization, we
use assumptions both on the calling node as well as on the callee root node to
avoid passing the reference (in the caller) and linking it (on the callee
side). This assumption is invalidated the first time the reference is actually
needed. But even then, often the `PFrame.Reference` doesn't hold on to anything
else, because it was only used for traversal, so this is pretty cheap even in
the not inlined case.

When an event forces the frame to materialize on the heap, the reference is
filled. This is usually only the case when someone uses `sys._getframe` or
accesses the traceback of an exception. If the stack is still live, we walk the
stack and insert the "calling node" and create a "PyFrame" object that mirrors
the locals in the Truffle frame. But we need to be able to do this also for
frames that are no longer live, e.g. when an exception was a few frames up. To
ensure this, we set a boolean flag on `PFrame.Reference` to mark it as "escaped"
when it is attached to an exception (or anything else), but not accessed,
yet. Whenever a Python call returns and its `PFrame.Reference` was marked such,
the "PyFrame" is also filled in by copying from the VirtualFrame. This way, the
stack is lazily forced to the heap as we return from functions. If we're lucky
and it is never actually accessed *and* the calls are all inlined, those fill-in
operations can be escape-analyzed away.

To implement all this, we use the ExecutionContext.CallContext and
ExecutionContext.CalleeContext classes. These also use profiling information to
eagerly fill in frame information if the callees actually access the stack, for
example, so that no further stack walks need to take place.

#### ExecutionContext.IndirectCallContext and ExecutionContext.IndirectCalleeContext

If we're mixing Python frames with non-Python frames, or if we are making calls
to methods and cannot pass the Truffle frame, we need to store the last
`PFrame.Reference` on the context so that, if we ever return back into a Python
function, it can properly link to the last frame. However, this is potentially
expensive, because it means storing a linked list of frames on the context. So
instead, we do it only lazily. When an "indirect" Python callee needs its
caller, it initially walks the stack to find it. But it will also tell the last
Python node that made a call to a "foreign" callee that it will have to store
its `PFrame.Reference` globally in the future for it to be available later.

### The current PException

Now that we have a mechanism to lazily make available only as much frame state
as needed, we use the same mechanism to also pass the currently handled
exception. Unlike CPython we do not use a stack of currently handled exceptions,
instead we utilize the call stack of Java by always passing the current exception
and holding on to the last (if any) in a local variable.

## Patching of Packages

Some PyPI packages contain code that is not compatible with GraalPython.
To overcome this limitation and support such packages, GraalPython contains
patches for some popular packages. The patches are applied to packages
installed via GraalPython specific utility `ginstall` and also to packages
installed via `pip`. This is achieved by patching `pip` code during the loading
of the `pip` module in `pip_hook.py`.

The patches are regular POSIX `patch` command compatible diffs located
in `lib-graalpython/patches`. The directory structure is following:

```
patches
├── atomicwrites
│   └── sdist
│       ├── atomicwrites.patch
│       └── atomicwrites-5.3.1.patch
└── pytest
    └── whl
        ├── pytest.dir
        ├── pytest.patch
        ├── pytest-5.dir
        ├── pytest-5.patch
        ├── pytest-5.2.dir
        ├── pytest-5.2.patch
        ├── pytest-5.2.3.dir
        └── pytest-5.2.3.patch
```

The directory names are names of the Python packages.

Patches in the `sdist` subdirectory apply to the source distribution tarballs,
i.e., such patches may patch, for example, the `setup.py` file.

Patches in the `whl` subdirectory apply to both the wheels binary distributions
and to appropriate subdirectory of the source distributions. For example, in `pytest`
the Python source files that are deployed into the top-level in the wheels binary
archive are located in the `src` subdirectory inside the source distribution. To reuse
one patch for both binary and source distributions, we need to also record the
subdirectory where the sources live in the source distribution - path to that directory
(relative to the root of the source distribution) is inside the `*.dir` files.

All the `*.patch` and `*.dir` files may contain version string. The search for
an appropriate `*.patch` file for a package `P` of version `X.Y.Z` happens in
this order:

* `P-X.Y.Z.patch`
* `P-X.Y.patch`
* `P-X.patch`
* `P.patch`

The same applies to `*.dir` files, and the search is independent of the search
for the `*.patch` file, i.e., multiple versions of `*.patch` may share one `*.dir`.

## The GIL

We always run with a GIL, because C extensions in CPython expect to do so and
are usually not written to be reentrant. The reason to always have the GIL
enabled is that when using Python, at least Sulong/LLVM is always available in
the same context and we cannot know if someone may be using that (or another
polyglot language or the Java host interop) to start additional threads that
could call back into Python. This could legitimately happen in C extensions when
the C extension authors use knowledge of how CPython works to do something
GIL-less in a C thread that is fine to do on CPython's data structures, but not
for ours.

Suppose we were running GIL-less until a second thread appears. There is now two
options: the second thread immediately takes the GIL, but both threads might be
executing in parallel for a little while before the first thread gets to the
next safepoint where they try to release and re-acquire the GIL. Option two is
that we block the second thread on some other semaphore until the first thread
has acquired the GIL. This scenario may deadlock if the first thread is
suspended in a blocking syscall. This could be a legitimate use case when
e.g. one thread is supposed to block on a `select` call that the other thread
would unblock by operating on the selected resource. Now the second thread
cannot start to run because it is waiting for the first thread to acquire the
GIL. To get around this potential deadlock, we would have to "remember" around
blocking calls that the first thread would have released the GIL before and
re-acquired it after that point. Since this is equivalent to just releasing and
re-acquiring the GIL, we might as well always do that.

The implication of this is that we may have to acquire and release the GIL
around all library messages that may be invoked without already holding the
GIL. The pattern here is, around every one of those messages:

```
@ExportMessage xxx(..., @Cached GilNode gil) {
    boolean mustRelease = gil.acquire();
    try {
        ...
    } finally {
        gil.release(mustRelease);
    }
}
```

The `GilNode` when used in this pattern ensures that we only release the GIL if
we acquired it before. The `GilNode` internally uses profiles to ensure that if
we are always running single threaded or always own the GIL already when we get
to a specific message we never emit a boundary call to lock and unlock the
GIL. This implies that we may deopt in some places if, after compiling some
code, we later start a second thread.
