/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.posix;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__FSPATH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.DT_UNKNOWN;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.S_IFMT;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFd;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFileHandle;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PNfiDirEntry)
public class NfiDirEntryBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return NfiDirEntryBuiltinsFactory.getFactories();
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.produceBytes()")
        PBytes nameAsBytes(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsBytes(getPosixSupport(), posixLib.dirEntryGetName(getPosixSupport(), self.dirEntryData), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = "!self.produceBytes()")
        String nameAsString(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.dirEntryGetName(getPosixSupport(), self.dirEntryData));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        String repr(VirtualFrame frame, PNfiDirEntry self,
                        @Cached NameNode nameNode,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprNode,
                        @Cached CastToJavaStringNode castToStringNode) {
            return "<DirEntry " + castToStringNode.execute(reprNode.executeObject(frame, nameNode.call(frame, self))) + ">";
        }
    }

    abstract static class GetOpaquePathHelperNode extends PythonBuiltinBaseNode {

        abstract Object execute(VirtualFrame frame, Object dirEntryData, PosixFileHandle path);

        @Specialization
        Object getName(VirtualFrame frame, Object dirEntryData, @SuppressWarnings("unused") PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryGetName(getPosixSupport(), dirEntryData);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization
        Object getPath(VirtualFrame frame, Object dirEntryData, PosixPath path,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryGetPath(getPosixSupport(), dirEntryData, path);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    abstract static class CachedPosixPathNode extends PythonBuiltinBaseNode {

        abstract PosixPath execute(VirtualFrame frame, PNfiDirEntry self);

        @Specialization(guards = "self.pathCache != null")
        PosixPath cached(PNfiDirEntry self) {
            return self.pathCache;
        }

        @Specialization(guards = {"self.pathCache == null", "self.produceBytes()"})
        PosixPath createBytes(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GetOpaquePathHelperNode getOpaquePathHelperNode) {
            Object opaquePath = getOpaquePathHelperNode.execute(frame, self.dirEntryData, self.scandirPath);
            self.pathCache = new PosixPath(posixLib.getPathAsBytes(getPosixSupport(), opaquePath, factory()), opaquePath, true);
            return self.pathCache;
        }

        @Specialization(guards = {"self.pathCache == null", "!self.produceBytes()"})
        PosixPath createString(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GetOpaquePathHelperNode getOpaquePathHelperNode) {
            Object opaquePath = getOpaquePathHelperNode.execute(frame, self.dirEntryData, self.scandirPath);
            self.pathCache = new PosixPath(posixLib.getPathAsString(getPosixSupport(), opaquePath), opaquePath, false);
            return self.pathCache;
        }
    }

    @Builtin(name = "path", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PathNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object path(VirtualFrame frame, PNfiDirEntry self,
                        @Cached CachedPosixPathNode cachedPosixPathNode) {
            return cachedPosixPathNode.execute(frame, self).originalObject;
        }
    }

    @Builtin(name = __FSPATH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FspathNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object fspath(VirtualFrame frame, PNfiDirEntry self,
                        @Cached PathNode pathNode) {
            return pathNode.call(frame, self);
        }
    }

    @Builtin(name = "inode", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        long inode(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryGetInode(getPosixSupport(), self.dirEntryData);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, varArgsMarker = true, keywordOnlyNames = {
                    "follow_symlinks"}, doc = "return stat_result object for the entry; cached per entry")
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class StatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NfiDirEntryBuiltinsClinicProviders.StatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object stat(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks,
                        @Cached StatHelperNode statHelperNode) {
            return statHelperNode.execute(frame, self, followSymlinks, false);
        }
    }

    abstract static class StatHelperNode extends PythonBuiltinBaseNode {

        abstract PTuple execute(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks, boolean catchNoent);

        @Specialization(guards = {"followSymlinks", "self.statCache != null"})
        @SuppressWarnings("unused")
        PTuple cachedStat(PNfiDirEntry self, boolean followSymlinks, boolean catchNoent) {
            return self.statCache;
        }

        @Specialization(guards = {"!followSymlinks", "self.lstatCache != null"})
        @SuppressWarnings("unused")
        PTuple cachedLStat(PNfiDirEntry self, boolean followSymlinks, boolean catchNoent) {
            return self.lstatCache;
        }

        @Specialization(guards = "self.getStatCache(followSymlinks) == null")
        PTuple stat(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks, boolean catchNoent,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached IsSymlinkNode isSymlinkNode,
                        @Cached StatHelperNode recursiveNode,
                        @Cached CachedPosixPathNode cachedPosixPathNode,
                        @Cached ConditionProfile positiveLongProfile,
                        @Cached ConditionProfile noSymlinkProfile) {
            PTuple res;
            // There are two caches - one for `follow_symlinks=True` and the other for
            // 'follow_symlinks=False`. They are different only when the dir entry is a symlink.
            // If it is not, they need to be the same, so we must make sure that fstatat() gets
            // called only once.
            if (noSymlinkProfile.profile(followSymlinks && !isSymlinkNode.execute(frame, self))) {
                // The entry is not a symlink, so both stat caches need to have the
                // same value. Also, the `follow_symlinks=False` cache might already be filled
                // in. (In fact, the call to isSymlinkNode in the condition may fill it.)
                // So we call ourselves recursively to either use or fill that cache first, and
                // the `follow_symlinks=True` cache will be filled below.
                res = recursiveNode.execute(frame, self, false, catchNoent);
            } else {
                int dirFd = self.scandirPath instanceof PosixFd ? ((PosixFd) self.scandirPath).fd : PosixSupportLibrary.DEFAULT_DIR_FD;
                PosixPath posixPath = cachedPosixPathNode.execute(frame, self);
                try {
                    long[] rawStat = posixLib.fstatAt(getPosixSupport(), dirFd, posixPath, followSymlinks);
                    res = PosixModuleBuiltins.createStatResult(factory(), positiveLongProfile, rawStat);
                } catch (PosixException e) {
                    if (catchNoent && e.getErrorCode() == OSErrorEnum.ENOENT.getNumber()) {
                        return null;
                    }
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            }
            self.setStatCache(followSymlinks, res);
            return res;
        }
    }

    abstract static class TestModeNode extends PythonBuiltinBaseNode {

        private final long expectedMode;
        private final int expectedDirEntryType;
        private StatHelperNode statHelperNode;

        protected TestModeNode(long expectedMode, int expectedDirEntryType) {
            this.expectedMode = expectedMode;
            this.expectedDirEntryType = expectedDirEntryType;
        }

        abstract boolean execute(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks);

        @Specialization(guards = "followSymlinks")
        boolean testModeUsingStat(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks) {
            PTuple statResult = getStatHelperNode().execute(frame, self, followSymlinks, true);
            if (statResult == null) {
                // file not found
                return false;
            }
            // TODO constants for stat_result indices
            long mode = (long) statResult.getSequenceStorage().getItemNormalized(0) & S_IFMT;
            return mode == expectedMode;
        }

        @Specialization(guards = "!followSymlinks")
        boolean useTypeIfKnown(VirtualFrame frame, PNfiDirEntry self, @SuppressWarnings("unused") boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            int entryType = posixLib.dirEntryGetType(getPosixSupport(), self.dirEntryData);
            if (entryType != DT_UNKNOWN) {
                return entryType == expectedDirEntryType;
            }
            return testModeUsingStat(frame, self, false);
        }

        private StatHelperNode getStatHelperNode() {
            if (statHelperNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                statHelperNode = insert(NfiDirEntryBuiltinsFactory.StatHelperNodeGen.create());
            }
            return statHelperNode;
        }

        static TestModeNode create(long expectedMode, int expectedDirEntryType) {
            return NfiDirEntryBuiltinsFactory.TestModeNodeGen.create(expectedMode, expectedDirEntryType);
        }
    }

    @Builtin(name = "is_symlink", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(PosixSupportLibrary.class)
    abstract static class IsSymlinkNode extends PythonUnaryBuiltinNode {

        abstract boolean execute(VirtualFrame frame, PNfiDirEntry self);

        @Specialization
        boolean isSymlink(VirtualFrame frame, PNfiDirEntry self,
                        @Cached("create(S_IFLNK, DT_LNK)") TestModeNode testModeNode) {
            return testModeNode.execute(frame, self, false);
        }
    }

    @Builtin(name = "is_file", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, varArgsMarker = true, keywordOnlyNames = {"follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    @ImportStatic(PosixSupportLibrary.class)
    abstract static class IsFileNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NfiDirEntryBuiltinsClinicProviders.IsFileNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean isFile(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks,
                        @Cached("create(S_IFREG, DT_REG)") TestModeNode testModeNode) {
            return testModeNode.execute(frame, self, followSymlinks);
        }
    }

    @Builtin(name = "is_dir", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, varArgsMarker = true, keywordOnlyNames = {"follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    @ImportStatic(PosixSupportLibrary.class)
    abstract static class IsDirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NfiDirEntryBuiltinsClinicProviders.IsDirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean isDir(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks,
                        @Cached("create(S_IFDIR, DT_DIR)") TestModeNode testModeNode) {
            return testModeNode.execute(frame, self, followSymlinks);
        }
    }
}
