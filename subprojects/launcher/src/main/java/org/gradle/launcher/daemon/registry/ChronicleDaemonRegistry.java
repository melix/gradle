/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.launcher.daemon.registry;

import net.openhft.chronicle.map.ChronicleMapBuilder;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.remote.Address;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Canceled;
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle;

public class ChronicleDaemonRegistry implements DaemonRegistry {

    private final ConcurrentMap<Address, DaemonInfo> daemonsByAddress;
    private final ConcurrentMap<Long, DaemonStopEvent> stopEvents;

    public ChronicleDaemonRegistry(File registryFile) {
        ChronicleMapBuilder<Address, DaemonInfo> daemonsBuilder =
            ChronicleMapBuilder.of(Address.class, DaemonInfo.class);
        daemonsBuilder.entries(512);
        try {
            daemonsByAddress = daemonsBuilder.createPersistedTo(registryFile);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        ChronicleMapBuilder<Long, DaemonStopEvent> stopEventsBuilder =
            ChronicleMapBuilder.of(Long.class, DaemonStopEvent.class);
        daemonsBuilder.entries(512);
        try {
            stopEvents = stopEventsBuilder.createPersistedTo(new File(registryFile.getParentFile(), registryFile.getName()+".events"));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public List<DaemonInfo> getAll() {
        Set<Map.Entry<Address, DaemonInfo>> entries = daemonsByAddress.entrySet();
        ArrayList<DaemonInfo> out = new ArrayList<DaemonInfo>(entries.size());
        for (Map.Entry<Address, DaemonInfo> entry : entries) {
            Address address = entry.getKey();
            DaemonInfo info = entry.getValue();
            info.setAddress(address);
            out.add(info);
        }
        return out;
    }

    public List<DaemonInfo> getIdle() {
        return getDaemonsMatching(new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() == Idle;
            }
        });
    }

    public List<DaemonInfo> getNotIdle() {
        return getDaemonsMatching(new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() != Idle;
            }
        });
    }

    public List<DaemonInfo> getCanceled() {
        return getDaemonsMatching(new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() == Canceled;
            }
        });
    }

    private List<DaemonInfo> getDaemonsMatching(Spec<DaemonInfo> spec) {
        List<DaemonInfo> out = new LinkedList<DaemonInfo>();
        List<DaemonInfo> all = getAll();
        for (DaemonInfo d : all) {
            if (spec.isSatisfiedBy(d)) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public void store(DaemonInfo info) {
        daemonsByAddress.put(info.getAddress(), info);
    }

    @Override
    public void remove(Address address) {
        daemonsByAddress.remove(address);
    }

    @Override
    public void markState(Address address, DaemonStateControl.State state) {
        DaemonInfo daemonInfo = daemonsByAddress.get(address);
        if (daemonInfo != null) {
            daemonInfo.setState(state);
            daemonsByAddress.put(address, daemonInfo);
        }
    }

    @Override
    public void storeStopEvent(DaemonStopEvent stopEvent) {
        stopEvents.put(stopEvent.getPid(), stopEvent);
    }

    @Override
    public List<DaemonStopEvent> getStopEvents() {
        return new ArrayList<DaemonStopEvent>(stopEvents.values());
    }

    @Override
    public void removeStopEvents(Collection<DaemonStopEvent> stopEventsToBeRemoved) {
        for (DaemonStopEvent stopEvent : stopEventsToBeRemoved) {
            stopEvents.remove(stopEvent.getPid());
        }
    }
}
