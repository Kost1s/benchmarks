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

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.agrona.concurrent.ShutdownSignalBarrier;

import java.io.IOException;
import java.time.Instant;
import java.util.Properties;

import static org.agrona.PropertyAction.PRESERVE;
import static org.agrona.PropertyAction.REPLACE;
import static io.aeron.benchmarks.grpc.remote.GrpcConfig.getServerBuilder;
import static io.aeron.benchmarks.util.PropertiesUtil.loadPropertiesFiles;
import static io.aeron.benchmarks.util.PropertiesUtil.mergeWithSystemProperties;

public class EchoServer implements AutoCloseable
{
    private final Server server;

    public EchoServer(final NettyServerBuilder serverBuilder)
    {
        server = serverBuilder.addService(new EchoService()).build();
    }

    public void start() throws IOException
    {
        server.start();
        System.out.println(Instant.now() + " [gRPC] server started, listening on: " + server.getListenSockets());
    }

    public void close() throws Exception
    {
        System.out.println(Instant.now() + " [gRPC] shutting down server...");
        server.shutdownNow();
        server.awaitTermination();
        System.out.println(Instant.now() + " [gRPC] shutdown complete.");
    }

    public static void main(final String[] args) throws Exception
    {
        mergeWithSystemProperties(PRESERVE, loadPropertiesFiles(new Properties(), REPLACE, args));

        try (EchoServer server = new EchoServer(getServerBuilder()))
        {
            server.start();

            new ShutdownSignalBarrier().await();
        }
    }
}
