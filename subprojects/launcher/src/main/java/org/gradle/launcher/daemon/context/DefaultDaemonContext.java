/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.context;

import com.google.common.base.Joiner;
import net.openhft.lang.io.Bytes;
import net.openhft.lang.io.serialization.BytesMarshallable;
import net.openhft.lang.model.constraints.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Keep in mind that this is a serialized value object.
 *
 * @see DaemonContextBuilder
 */
public class DefaultDaemonContext implements DaemonContext, BytesMarshallable {

    private String uid;
    private File javaHome;
    private File daemonRegistryDir;
    private Long pid;
    private Integer idleTimeout;
    private List<String> daemonOpts;

    public DefaultDaemonContext() {
    }

    public DefaultDaemonContext(String uid, File javaHome, File daemonRegistryDir, Long pid, Integer idleTimeout, List<String> daemonOpts) {
        this.uid = uid;
        this.javaHome = javaHome;
        this.daemonRegistryDir = daemonRegistryDir;
        this.pid = pid;
        this.idleTimeout = idleTimeout;
        this.daemonOpts = daemonOpts;
    }

    public String toString() {
        return String.format("DefaultDaemonContext[uid=%s,javaHome=%s,daemonRegistryDir=%s,pid=%s,idleTimeout=%s,daemonOpts=%s]",
            uid, javaHome, daemonRegistryDir, pid, idleTimeout, Joiner.on(',').join(daemonOpts));
    }

    public String getUid() {
        return uid;
    }

    public File getJavaHome() {
        return javaHome;
    }

    public File getDaemonRegistryDir() {
        return daemonRegistryDir;
    }

    public Long getPid() {
        return pid;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public List<String> getDaemonOpts() {
        return daemonOpts;
    }

    @Override
    public void readMarshallable(@NotNull Bytes in) throws IllegalStateException {
        uid = in.readUTFΔ();
        javaHome = new File(in.readUTF());
        daemonRegistryDir = new File(in.readUTF());
        pid = in.readBoolean() ? in.readLong() : null;
        idleTimeout = in.readBoolean() ? in.readInt() : null;
        int optCount = in.readInt();
        daemonOpts = new ArrayList<String>(optCount);
        for (int i = 0; i < optCount; i++) {
            daemonOpts.add(in.readUTF());
        }
    }

    @Override
    public void writeMarshallable(@NotNull Bytes out) {
        out.writeUTFΔ(uid);
        out.writeUTF(javaHome.getAbsolutePath());
        out.writeUTF(daemonRegistryDir.getAbsolutePath());
        out.writeBoolean(pid != null);
        if (pid != null) {
            out.writeLong(pid);
        }
        out.writeBoolean(idleTimeout != null);
        if (idleTimeout != null) {
            out.writeInt(idleTimeout);
        }
        out.writeInt(daemonOpts.size());
        for (String daemonOpt : daemonOpts) {
            out.writeUTF(daemonOpt);
        }
    }
}
