/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.advanced.grpc;

import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class GeneratedGatewayStubExample {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization",
            Metadata.ASCII_STRING_MARSHALLER);

    private GeneratedGatewayStubExample() {
    }

    public static void main(String[] args) throws InterruptedException {
        URI address = URI.create(requiredEnvironmentVariable("CAMUNDA_GRPC_ADDRESS"));
        ManagedChannel channel = createChannel(address);

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(Duration.ofSeconds(10));

            String token = System.getenv("CAMUNDA_GRPC_TOKEN");
            if (token != null && !token.isBlank()) {
                Metadata headers = new Metadata();
                headers.put(AUTHORIZATION, "Bearer " + token);
                stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
            }

            TopologyResponse topology = stub.topology(TopologyRequest.getDefaultInstance());
            topology.getBrokersList().forEach(broker -> System.out.printf(
                    "Broker %d: %s:%d (%s)%n",
                    broker.getNodeId(),
                    broker.getHost(),
                    broker.getPort(),
                    broker.getVersion()));
        } catch (StatusRuntimeException e) {
            System.err.printf("Topology request failed with gRPC status %s%n", e.getStatus());
            throw e;
        } finally {
            channel.shutdown();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("gRPC channel did not terminate");
                }
            }
        }
    }

    private static ManagedChannel createChannel(URI address) {
        if (address.getHost() == null) {
            throw new IllegalArgumentException("CAMUNDA_GRPC_ADDRESS must include a host");
        }
        String scheme = address.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "CAMUNDA_GRPC_ADDRESS must use the http or https scheme");
        }

        int port = address.getPort();
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(
                address.getHost(),
                port >= 0 ? port : defaultPort(scheme));

        return "https".equalsIgnoreCase(scheme)
                ? builder.useTransportSecurity().build()
                : builder.usePlaintext().build();
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " is required");
        }
        return value;
    }
}
