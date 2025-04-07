/*
 * Copyright 2015-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.benchmarks.aeron.remote;

import io.aeron.RethrowingErrorHandler;
import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import org.HdrHistogram.ValueRecorder;
import org.agrona.IoUtil;
import org.agrona.concurrent.NanoClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.archive.client.AeronArchive.connect;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static io.aeron.benchmarks.aeron.remote.ArchivingMediaDriver.launchArchiveWithEmbeddedDriver;

class LiveReplayTest extends
    AbstractTest<ArchivingMediaDriver, AeronArchive, LiveReplayMessageTransceiver, ArchiveNode>
{

    private File archiveDir;

    @BeforeEach
    void before()
    {
        setProperty(AeronArchive.Configuration.CONTROL_CHANNEL_PROP_NAME,
            "aeron:udp?endpoint=localhost:8010|term-length=64k");
        setProperty(AeronArchive.Configuration.CONTROL_RESPONSE_CHANNEL_PROP_NAME,
            "aeron:udp?endpoint=localhost:8020");
        setProperty(Archive.Configuration.REPLICATION_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:8040");
    }

    @AfterEach
    void after()
    {
        clearProperty(AeronArchive.Configuration.CONTROL_CHANNEL_PROP_NAME);
        clearProperty(AeronArchive.Configuration.CONTROL_RESPONSE_CHANNEL_PROP_NAME);
        clearProperty(Archive.Configuration.REPLICATION_CHANNEL_PROP_NAME);
        IoUtil.delete(archiveDir, true);
    }

    protected ArchiveNode createNode(
        final AtomicBoolean running, final ArchivingMediaDriver archivingMediaDriver, final AeronArchive aeronArchive)
    {
        return new ArchiveNode(running, archivingMediaDriver, aeronArchive, false);
    }

    protected ArchivingMediaDriver createDriver()
    {
        final ArchivingMediaDriver driver = launchArchiveWithEmbeddedDriver();
        archiveDir = driver.archive.context().archiveDir();
        return driver;
    }

    protected AeronArchive connectToDriver()
    {
        return connect(new AeronArchive.Context().errorHandler(new RethrowingErrorHandler()));
    }

    protected Class<LiveReplayMessageTransceiver> messageTransceiverClass()
    {
        return LiveReplayMessageTransceiver.class;
    }

    protected LiveReplayMessageTransceiver createMessageTransceiver(
        final NanoClock nanoClock,
        final ValueRecorder valueRecorder,
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive)
    {
        return new LiveReplayMessageTransceiver(nanoClock, valueRecorder, null, aeronArchive, false);
    }
}
