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

package org.gradle.launcher.daemon.registry

import groovy.transform.ToString
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.remote.Address
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DaemonContextBuilder
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Busy
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle

class ChronicleDaemonRegistryTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()

    int addressCounter = 0
    def file = tmp.file("registry")
    def registry = new ChronicleDaemonRegistry(file)

    def "safely removes from registry file"() {
        given:
        def address = address()

        and:
        registry.store(new DaemonInfo(address, daemonContext(), "password".bytes, Idle))

        when:
        registry.remove(address)

        then:
        registry.all.empty

        and: //it is safe to remove it again
        registry.remove(address)
    }

    def "stores daemons into registry file"() {
        given:
        def address = address()

        and:
        registry.store(new DaemonInfo(address, daemonContext(), "password".bytes, Idle))

        when:
        def daemon = registry.all[0]

        then:
        daemon.address == address
        daemon.state == Idle
        daemon.token == "password".bytes
    }

    def "safely removes if registry empty"() {
        given:
        def address = address()

        when:
        registry.remove(address)

        then:
        registry.all.empty
    }

    def "mark busy ignores entry that has been removed"() {
        given:
        def address = address()

        when:
        registry.markState(address, Busy)

        then:
        registry.all.empty
    }

    def "mark idle ignores entry that has been removed"() {
        given:
        def address = address()

        when:
        registry.markState(address, Idle)

        then:
        registry.all.empty
    }

    def "safely removes stop events when empty"() {
        when:
        registry.removeStopEvents([])

        then:
        registry.stopEvents.empty
    }

    def "clears single stop event when non-empty"() {
        given:
        def stopEvent = new DaemonStopEvent(new Date(1L), new Random().nextLong(), DaemonExpirationStatus.GRACEFUL_EXPIRE, "STOP_REASON")
        registry.storeStopEvent(stopEvent)

        when:
        registry.removeStopEvents([stopEvent])

        then:
        registry.stopEvents.empty
    }

    def "clears multiple stop events when non-empty"() {
        given:
        def stopEvents = [
            new DaemonStopEvent(new Date(1L), new Random().nextLong(), DaemonExpirationStatus.GRACEFUL_EXPIRE, "STOP_REASON"),
            new DaemonStopEvent(new Date(42L), new Random().nextLong(), DaemonExpirationStatus.IMMEDIATE_EXPIRE, "ANOTHER_STOP_REASON")
        ]
        stopEvents.each { registry.storeStopEvent(it) }

        when:
        registry.removeStopEvents(stopEvents)

        then:
        registry.stopEvents.empty
    }

    DaemonContext daemonContext() {
        new DaemonContextBuilder([maybeGetPid: {null}] as ProcessEnvironment).with {
            daemonRegistryDir = tmp.createDir("daemons")
            create()
        }
    }

    Address address(int i = addressCounter++) {
        new TestAddress(i.toString())
    }

    @ToString(includeNames = true)
    private static class TestAddress implements Address {

        final String displayName

        TestAddress(String displayName) {
            this.displayName = displayName
        }

        boolean equals(o) {
            displayName == o.displayName
        }

        int hashCode() {
            displayName.hashCode()
        }
    }

}
