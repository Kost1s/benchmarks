/*
 * Copyright 2015-2024 Real Logic Limited.
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
package uk.co.real_logic.benchmarks.aeron.remote;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.RethrowingErrorHandler;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.ValueRecorder;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SystemNanoClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.LoadTestRig;
import uk.co.real_logic.benchmarks.remote.PersistedHistogram;
import uk.co.real_logic.benchmarks.remote.SinglePersistedHistogram;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.aeron.Aeron.connect;
import static java.lang.System.setProperty;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

class EchoTest extends AbstractTest<MediaDriver, Aeron, EchoMessageTransceiver, EchoNode>
{
    protected EchoNode createNode(final AtomicBoolean running, final MediaDriver mediaDriver, final Aeron aeron)
    {
        return new EchoNode(running, mediaDriver, aeron, false, 0);
    }

    protected MediaDriver createDriver()
    {
        return launchDriver(new MediaDriver.Context().threadingMode(ThreadingMode.SHARED));
    }

    private static MediaDriver launchDriver(final MediaDriver.Context ctx)
    {
        return MediaDriver.launch(ctx
            .dirDeleteOnStart(true)
            .spiesSimulateConnection(true));
    }

    protected Aeron connectToDriver()
    {
        return connectToDriver(CommonContext.AERON_DIR_PROP_DEFAULT);
    }

    private static Aeron connectToDriver(final String aeronDir)
    {
        return connect(new Aeron.Context()
            .aeronDirectoryName(aeronDir)
            .errorHandler(new RethrowingErrorHandler()));
    }

    protected Class<EchoMessageTransceiver> messageTransceiverClass()
    {
        return EchoMessageTransceiver.class;
    }

    protected EchoMessageTransceiver createMessageTransceiver(
        final NanoClock nanoClock,
        final ValueRecorder valueRecorder,
        final MediaDriver mediaDriver,
        final Aeron aeron)
    {
        return new EchoMessageTransceiver(nanoClock, valueRecorder, mediaDriver, aeron, false);
    }

    @TempDir
    private Path tempDir;

    @Timeout(10)
    @Test
    void ipcChannels(final @TempDir Path tempDir) throws Exception
    {
        setProperty(SOURCE_CHANNEL_PROP_NAME, "aeron:ipc");
        setProperty(DESTINATION_CHANNEL_PROP_NAME, "aeron:ipc");
        test(1000, 333, 1, tempDir);
    }

    @Timeout(10)
    @Test
    void multipleDestinations() throws Exception
    {
        final int numDestinations = 2;
        setProperty(SOURCE_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:20202");
        setProperty(DESTINATION_CHANNEL_PROP_NAME,
            "aeron:udp?control=localhost:10101|control-mode=dynamic|fc=min,g:/" + numDestinations +
            "|group=true|term-length=64k");
        setProperty(NUMBER_OF_RECEIVERS_PROP_NAME, Integer.toString(numDestinations));
        final Configuration configuration = new Configuration.Builder()
            .warmupIterations(1)
            .warmupMessageRate(1)
            .iterations(1)
            .messageRate(30)
            .messageLength(288)
            .messageTransceiverClass(messageTransceiverClass())
            .batchSize(1)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("aeron")
            .build();

        final AtomicReference<Throwable> error = new AtomicReference<>();

        try (MediaDriver driver = launchDriver(new MediaDriver.Context()
            .aeronDirectoryName(CommonContext.generateRandomDirName())
            .threadingMode(ThreadingMode.SHARED_NETWORK)
            .conductorIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .sharedNetworkIdleStrategy(NoOpIdleStrategy.INSTANCE));
            Aeron client = connectToDriver(driver.aeronDirectoryName()))
        {
            final AtomicBoolean running = new AtomicBoolean(true);
            final CountDownLatch echoNodesStarted = new CountDownLatch(numDestinations);
            final List<Thread> threads = IntStream.range(0, numDestinations).mapToObj(i ->
            {
                final Thread thread = new Thread(
                    () ->
                    {
                        echoNodesStarted.countDown();

                        try (MediaDriver nodeDriver = launchDriver(new MediaDriver.Context()
                            .aeronDirectoryName(CommonContext.generateRandomDirName())
                            .threadingMode(ThreadingMode.SHARED_NETWORK)
                            .conductorIdleStrategy(NoOpIdleStrategy.INSTANCE)
                            .sharedNetworkIdleStrategy(NoOpIdleStrategy.INSTANCE));
                            Aeron nodeClient = connectToDriver(nodeDriver.aeronDirectoryName());
                            EchoNode node = new EchoNode(running, nodeDriver, nodeClient, false, i))
                        {
                            node.run();
                        }
                        catch (final Throwable t)
                        {
                            if (null == error.get() && error.compareAndSet(null, t))
                            {
                                return;
                            }
                            error.get().addSuppressed(t);
                        }
                    });
                thread.setDaemon(true);
                thread.setName("destination-" + i);
                thread.start();
                return thread;
            }).collect(Collectors.toList());
            try
            {
                final NanoClock nanoClock = SystemNanoClock.INSTANCE;
                final PersistedHistogram persistedHistogram = new SinglePersistedHistogram(new Histogram(3));

                final ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
                final PrintStream out = new PrintStream(baos, false, StandardCharsets.US_ASCII.name());

                final LoadTestRig loadTestRig = new LoadTestRig(
                    configuration,
                    nanoClock,
                    persistedHistogram,
                    (nc, ph) -> createMessageTransceiver(nc, ph, driver, client),
                    out);

                echoNodesStarted.await();
                loadTestRig.run();

                final String ouptput = baos.toString();
                assertEquals(-1, ouptput.indexOf("WARNING:"), ouptput);
            }
            finally
            {
                running.set(false);
                Thread.interrupted(); // clear interrupt
                for (final Thread thread : threads)
                {
                    thread.join();
                }
            }
        }

        if (null != error.get())
        {
            rethrowUnchecked(error.get());
        }
    }
}
