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

package io.aeron.benchmarks.grpc.remote;

import org.HdrHistogram.Histogram;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import io.aeron.benchmarks.remote.Configuration;
import io.aeron.benchmarks.remote.LoadTestRig;
import io.aeron.benchmarks.remote.PersistedHistogram;
import io.aeron.benchmarks.remote.SinglePersistedHistogram;

import java.io.PrintStream;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.mockito.Mockito.mock;
import static io.aeron.benchmarks.grpc.remote.GrpcConfig.*;

class GrpcTest
{
    @BeforeEach
    void before()
    {
        setProperty(CERTIFICATES_DIR_PROP_NAME, Configuration.tryResolveCertificatesDirectory().toString());
    }

    @AfterEach
    void after()
    {
        clearProperty(SERVER_HOST_PROP_NAME);
        clearProperty(SERVER_PORT_PROP_NAME);
        clearProperty(TLS_PROP_NAME);
        clearProperty(CERTIFICATES_DIR_PROP_NAME);
    }

    @Test
    @Timeout(30)
    void messageLength32bytes() throws Exception
    {
        test(10_000, 32, 10, false);
    }

    @Test
    @Timeout(30)
    void messageLength1344bytes() throws Exception
    {
        test(100, 1344, 1, true);
    }

    @Test
    @Timeout(30)
    void messageLength288bytes() throws Exception
    {
        test(777, 288, 3, false);
    }

    private void test(
        final int numberOfMessages,
        final int messageLength,
        final int burstSize,
        final boolean useTls) throws Exception
    {
        setProperty(TLS_PROP_NAME, Boolean.toString(useTls));
        try (EchoServer server = new EchoServer(getServerBuilder()))
        {
            server.start();

            final NanoClock nanoClock = SystemNanoClock.INSTANCE;
            final PersistedHistogram persistedHistogram = new SinglePersistedHistogram(new Histogram(3));

            final Configuration configuration = new Configuration.Builder()
                .warmupIterations(0)
                .iterations(1)
                .messageRate(numberOfMessages)
                .batchSize(burstSize)
                .messageLength(messageLength)
                .messageTransceiverClass(StreamingMessageTransceiver.class)
                .outputFileNamePrefix("grpc")
                .build();

            final LoadTestRig loadTestRig = new LoadTestRig(
                configuration,
                nanoClock,
                persistedHistogram,
                mock(PrintStream.class));

            loadTestRig.run();
        }
    }
}
