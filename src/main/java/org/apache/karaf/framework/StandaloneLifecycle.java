/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.framework;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.karaf.framework.deployer.OSGiBundleLifecycle;
import org.apache.karaf.framework.scanner.StandaloneScanner;
import org.apache.karaf.framework.service.BundleRegistry;
import org.apache.karaf.framework.service.OSGiServices;

public class StandaloneLifecycle implements AutoCloseable {

    private final OSGiServices services = new OSGiServices();
    private final BundleRegistry registry = new BundleRegistry();

    public synchronized StandaloneLifecycle start() {
        registry.getBundles().putAll(new StandaloneScanner()
                .findOSGiBundles()
                .stream()
                .map(it -> new OSGiBundleLifecycle(it.getManifest(), it.getJar(), services, registry))
                .peek(OSGiBundleLifecycle::start)
                .collect(toMap(b -> b.getBundle().getBundleId(), identity())));
        return this;
    }

    public synchronized void stop() {
        final Map<Long, OSGiBundleLifecycle> bundles = registry.getBundles();
        bundles.forEach((k, v) -> v.stop());
        bundles.clear();
    }

    @Override // for try with resource syntax
    public void close() {
        stop();
    }

    public static void main(final String[] args) {
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {

            {
                setName(getClass().getName() + "-shutdown-hook");
            }

            @Override
            public void run() {
                latch.countDown();
            }
        });
        try (final StandaloneLifecycle lifecycle = new StandaloneLifecycle().start()) {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
