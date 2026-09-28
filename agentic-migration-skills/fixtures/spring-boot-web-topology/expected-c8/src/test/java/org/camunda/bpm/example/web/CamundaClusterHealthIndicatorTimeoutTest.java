/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = CamundaClusterHealthIndicatorTimeoutTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CamundaClusterHealthIndicatorTimeoutTest {

  private static final DelayedCluster CLUSTER = startCluster();

  @Autowired private CamundaClusterHealthIndicator healthIndicator;

  @DynamicPropertySource
  static void clusterAddress(DynamicPropertyRegistry registry) {
    registry.add("camunda.client.rest-address", CLUSTER::address);
  }

  @AfterAll
  static void stopCluster() throws IOException {
    CLUSTER.close();
  }

  @Test
  void reportsDownWhenTheClusterDoesNotRespondBeforeTheReadTimeout() throws InterruptedException {
    Status status = healthIndicator.health().getStatus();

    assertThat(CLUSTER.requestReceived.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(status).isEqualTo(Status.DOWN);
    assertThat(CLUSTER.failure.get()).isNull();
  }

  @Configuration
  @EnableAutoConfiguration(
      excludeName = "io.camunda.client.spring.configuration.CamundaAutoConfiguration")
  @Import(CamundaClusterHealthIndicator.class)
  static class TestApplication {}

  private static DelayedCluster startCluster() {
    try {
      return new DelayedCluster();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static class DelayedCluster {

    private final ServerSocket serverSocket;
    private final Thread serverThread;
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final AtomicReference<IOException> failure = new AtomicReference<>();

    private DelayedCluster() throws IOException {
      serverSocket = new ServerSocket();
      serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));

      serverThread = new Thread(this::serve, "delayed-camunda-health-server");
      serverThread.setDaemon(true);
      serverThread.start();
    }

    private String address() {
      return "http://127.0.0.1:" + serverSocket.getLocalPort();
    }

    private void serve() {
      try (Socket socket = serverSocket.accept();
          BufferedReader request =
              new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
        String line;
        while ((line = request.readLine()) != null && !line.isEmpty()) {}
        if (line == null) {
          throw new IOException("The client closed before sending complete request headers");
        }
        requestReceived.countDown();
        Thread.sleep(2_000);
        socket
            .getOutputStream()
            .write(
                "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } catch (IOException exception) {
        if (requestReceived.getCount() != 0) {
          failure.set(exception);
        }
      }
    }

    private void close() throws IOException {
      serverThread.interrupt();
      serverSocket.close();
    }
  }
}
