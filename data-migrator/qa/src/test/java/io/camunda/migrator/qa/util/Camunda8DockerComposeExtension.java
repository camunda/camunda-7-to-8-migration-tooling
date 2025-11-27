/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.util;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * JUnit extension that starts Camunda 8 once via Docker Compose before all tests
 * and shares it across all test classes. The container is started only once
 * per test run using a static block.
 * 
 * This extension sets up system properties to configure the Camunda Process Test
 * library to use REMOTE mode with the shared C8 instance.
 */
public class Camunda8DockerComposeExtension implements BeforeAllCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(Camunda8DockerComposeExtension.class);

  public static final int CAMUNDA8_GRPC_PORT = 26500;
  public static final int CAMUNDA8_REST_PORT = 8080;
  public static final int CAMUNDA8_MONITORING_PORT = 9600;
  public static final String CAMUNDA8_SERVICE = "camunda8";
  public static final String ELASTICSEARCH_SERVICE = "elasticsearch";

  private static ComposeContainer container;
  private static volatile boolean started = false;
  private static final Object LOCK = new Object();

  static {
    startContainers();
  }

  private static void startContainers() {
    synchronized (LOCK) {
      if (started) {
        return;
      }

      URL resource = Camunda8DockerComposeExtension.class.getClassLoader()
          .getResource("docker-compose/docker-compose-c8.yml");

      if (resource == null) {
        throw new IllegalStateException("Docker Compose file not found: docker-compose/docker-compose-c8.yml");
      }

      String dockerComposeFile = resource.getFile();

      LOGGER.info("Starting Camunda 8 via Docker Compose from: {}", dockerComposeFile);

      container = new ComposeContainer(new File(dockerComposeFile))
          .withExposedService(CAMUNDA8_SERVICE, CAMUNDA8_GRPC_PORT,
              Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
          .withExposedService(CAMUNDA8_SERVICE, CAMUNDA8_REST_PORT,
              Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
          .withExposedService(CAMUNDA8_SERVICE, CAMUNDA8_MONITORING_PORT,
              Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
          .withExposedService(ELASTICSEARCH_SERVICE, 9200,
              Wait.forHttp("/_cat/health")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

      container.start();
      started = true;

      // Set system properties for Camunda Process Test to use REMOTE mode
      System.setProperty("camunda.process-test.runtime-mode", "REMOTE");
      System.setProperty("camunda.process-test.remote.client.grpc-address", getGrpcAddress());
      System.setProperty("camunda.process-test.remote.client.rest-address", getRestAddress());
      System.setProperty("camunda.process-test.remote.camunda-monitoring-api-address", getMonitoringAddress());

      // Also set Camunda client properties for the migrator
      System.setProperty("camunda.client.grpc-address", getGrpcAddress());
      System.setProperty("camunda.client.rest-address", getRestAddress());

      LOGGER.info("Camunda 8 started - gRPC: {}, REST: {}, Monitoring: {}",
          getGrpcAddress(), getRestAddress(), getMonitoringAddress());

      // Register shutdown hook to stop containers when JVM exits
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        if (container != null) {
          LOGGER.info("Stopping Camunda 8 Docker Compose containers");
          container.stop();
        }
      }));
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    // Container is already started in static block
    // This method ensures the extension is registered and system properties are set
  }

  public static String getHost() {
    return container.getServiceHost(CAMUNDA8_SERVICE, CAMUNDA8_GRPC_PORT);
  }

  public static int getGrpcPort() {
    return container.getServicePort(CAMUNDA8_SERVICE, CAMUNDA8_GRPC_PORT);
  }

  public static int getRestPort() {
    return container.getServicePort(CAMUNDA8_SERVICE, CAMUNDA8_REST_PORT);
  }

  public static int getMonitoringPort() {
    return container.getServicePort(CAMUNDA8_SERVICE, CAMUNDA8_MONITORING_PORT);
  }

  public static String getGrpcAddress() {
    return "http://" + getHost() + ":" + getGrpcPort();
  }

  public static String getRestAddress() {
    return "http://" + getHost() + ":" + getRestPort();
  }

  public static String getMonitoringAddress() {
    return "http://" + getHost() + ":" + getMonitoringPort();
  }

  public static boolean isStarted() {
    return started;
  }
}
