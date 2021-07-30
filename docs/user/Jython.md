---
layout: docs-experimental
toc_group: python
link_title: Jython Compatibility
permalink: /reference-manual/python/Jython/
---
# Jython Migration Guide

Most Jython code that uses Java integration will be based on a
stable Jython release, and these only come in Python 2.x versions.
GraalVM's Python runtime, in contrast, is only targeting Python 3.x.
GraalVM does not provide a full compatibility with these earlier 2.x versions of Jython.
Thus, a significant migration step will have to be taken to migrate all your code to Python 3.

For Jython specific features, follow this document to learn about migration to GraalVM's Python runtime.

Note that some features of Jython have a negative impact on runtime performance, and are disabled by default.
To make migration easier, you can enable some features with a command line flag on GraalVM: `--python.EmulateJython`.

## Importing Java Packages

There are certain features of Jython's Java integration that are enabled by default on GraalVM's Python runtime.
Here is an example:

    >>> import java.awt as awt
    >>> win = awt.Frame()
    >>> win.setSize(200, 200)
    >>> win.setTitle("Hello from Python!")
    >>> win.getSize().toString()
    'java.awt.Dimension[width=200,height=200]'
    >>> win.show()

This example works exactly the same on both Jython and Python on GraalVM.
However, on GraalVM only packages in the `java` namespace can be directly imported.
Importing classes from packages outside the `java` namespace also requires the `--python.EmulateJython` option to be active.

Additionally, importing Java packages as Python modules is only supported under very specific circumstances.
For example, this will work:
```python
import java.lang as lang
```

This will not work:
```python
import javax.swing as swing
from javax.swing import *
```

Instead, you will have to import one of the classes you are interested in directly:
```python
import javax.swing.Window as Window
```

## Basic Object Usage

Constructing and working with Java objects and classes is done with natural
Python syntax. The methods of Java objects can also be retrieved and passed
around as first class objects (bound to their instance), the same as Python
methods:

    >>> from java.util import Random
    >>> rg = Random(99)
    >>> boundNextInt = rg.nextInt
    >>> rg.nextInt()
    1491444859
    >>> boundNextInt = rg.nextInt
    1672896916

## Java-to-Python Types: Automatic Conversion

Method overloads are resolved by matching the Python arguments in a best-effort manner to the available parameter types.
This also happens during when data conversion.
The goal here is to make using Java from Python as smooth as possible.
The matching allowed here is similar to Jython, but GraalVM's Python runtime uses a more dynamic approach to matching &mdash; Python types emulating `int` or `float` are also converted to the appropriate Java types.
This allows, for example, to use Pandas frames as `double[][]` or NumPy array elements as `int[]` when the elements fit into those Java primitive types.

| Java type              | Python type                                                                       |
|:-----------------------|:----------------------------------------------------------------------------------|
| null                   | None                                                                              |
| boolean                | bool                                                                              |
| byte, short, int, long | int, any object that has an `__int__` method                                      |
| float                  | float, any object that has a `__float__` method                                   |
| char                   | str of length 1                                                                   |
| java.lang.String       | str                                                                               |
| byte[]                 | bytes, bytearray, wrapped Java array, Python list with only the appropriate types |
| Java arrays            | Wrapped Java array or Python list with only the appropriate types                 |
| Java objects           | Wrapped Java object of the appropriate type                                       |
| java.lang.Object       | Any object                                                                        |

## Special Jython Modules

None of the special Jython modules are available, but many of those modules functions can still be achieved.
For example, the `jarray` module on Jython allows construction of primitive Java arrays.
This can beachieved as follows on GraalVM's Python runtime:

    >>> import java
    >>> java.type("int[]")(10)

The code that only needs to pass a Java array can also use Python types.
However, implicitly, this may entail a copy of the array data, which can be deceiving when using Java arrays as output parameters:

    >>> i = java.io.ByteArrayInputStream(b"foobar")
    >>> buf = [0, 0, 0]
    >>> i.read(buf) # buf is automatically converted to a byte[] array
    3
    >>> buf
    [0, 0, 0] # the converted byte[] array got lost
    >>> jbuf = java.type("byte[]")(3)
    >>> i.read(jbuf)
    3
    >>> jbuf
    [98, 97, 122]

## Exceptions from Java

Catching all kinds of Java exceptions comes with a performance penalty and is only enabled with the `--python.EmulateJython` option.

    >>> import java
    >>> v = java.util.Vector()
    >>> try:
    ...    x = v.elementAt(7)
    ... except java.lang.ArrayIndexOutOfBoundsException as e:
    ...    print(e.getMessage())
    ...
    7 >= 0

## Java Collections

There is no automatic mapping of the Python syntax for accessing dictionary
elements to the `java.util` mapping and list classes' ` get`, `set`, or `put`
methods. To use these mapping and list clases, you must call the Java methods:

    >>> ht = java.util.Hashtable()
    >>> ht.put("foo", "bar")
    >>> ht.get("foo")
    'bar'

The Python-style iteration of Java `java.util.Enumerable`,
`java.util.Iterator`, or `java.lang.Iterable`  is not supported. For these, you will have to use a
`while` loop and use the `hasNext()` and `next()` (or equivalent) methods. <!---this doesn't want an example?--->

## Inheritance from Java

Inheriting from a Java class or implementing an interface is supported with some syntactical differences from Jython. A
class inheriting from a Java class can be created using an ordinary `class` statement where declared methods will
override/implement the superclass methods when they match in name. Super calls are performed using a special
attribute `self.__super__`. The created object won't behave like a python object but like a foreign Java object. Its
Python-level members can be accessed using its `this` attribute. Example:

```python
import atexit
from java.util.logging import Logger, Handler


class MyHandler(Handler):
    def __init__(self):
        self.logged = []

    def publish(self, record):
        self.logged.append(record)


logger = Logger.getLogger("mylog")
logger.setUseParentHandlers(False)
handler = MyHandler()
logger.addHandler(handler)
# Make sure the handler is not used after the Python context has been closed
atexit.register(lambda: logger.removeHandler(handler))

logger.info("Hi")
logger.warning("Bye")

# The python attributes/methods of the object have to be accessed through 'this' attribute
for record in handler.this.logged:
    print(f'Python captured message "{record.getMessage()}" at level {record.getLevel().getName()}')
```

## Embedding Python into Java

The other way to use Jython is to embed it into Java applications.
Where above GraalVM's Python runtime offered some measure of compatibility with existing Jython code, nothing is offered in this case.
Existing code using Jython depends directly on the Jython package (for example, in the Maven configuration), because the Java code has references to Jython internal classes such as `PythonInterpreter`.

For GraalVM's Python runtime, no dependency other than on the [GraalVM SDK](https://mvnrepository.com/artifact/org.graalvm.sdk/graal-sdk) is required.
There are no APIs particular to Python that are exposed, and everything is done through the GraalVM API.

It is important to note that as long as your application is executed on GraalVM with the Python language installed,
you can embed Python in your programs.
For more details, refer to the [Embed Languages](https://www.graalvm.org/reference-manual/embed-languages/) guide.
