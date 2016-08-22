/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class DefaultFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter {
    private final static File LOGFILE = new File("/tmp/snapshot.txt");

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver) {
        super(snapshotter, cacheAccess, stringInterner, fileResolver);
    }

    @Override
    protected void visitFiles(FileCollection input, final List<FileTreeElement> fileTreeElements, final List<FileTreeElement> missingFiles) {
        visitFiles(input, new Action<FileTreeElement>() {
            @Override
            public void execute(FileTreeElement fileTreeElement) {
                fileTreeElements.add(fileTreeElement);
            }
        }, null);
    }

    @Override
    protected void visitFiles(FileCollection input, Action<? super FileTreeElement> onExistingFile, Action<? super FileTreeElement> onMissingFile) {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(fileResolver);
        context.add(input);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();

        for (FileTreeInternal fileTree : fileTrees) {
            visitTreeForSnapshotting(fileTree, onExistingFile);
        }
    }

    private void visitTreeForSnapshotting(FileTreeInternal fileTree, final Action<? super FileTreeElement> action) {
        //final StringBuilder sb = new StringBuilder("Snapshotting\n------------\n");
        //new RuntimeException().printStackTrace(new PrintWriter(new StringBuilderWriter(sb)));
        fileTree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                action.execute(dirDetails);
                //sb.append("Dir:  ").append(dirDetails.getFile().getAbsolutePath()).append("\n");
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                action.execute(fileDetails);
                //sb.append("File: ").append(fileDetails.getFile().getAbsolutePath()).append("\n");
            }
        });
        //log(sb.toString());
    }

    private static void log(String str) {
        try {
            FileWriter wrt = new FileWriter(LOGFILE, true);
            wrt.append(str);
            wrt.close();
        } catch (IOException e) {
            // d
        }
    }
}
