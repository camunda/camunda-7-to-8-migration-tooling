/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.incident;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

public final class EngineConfigurationFactory {

  public static ProcessEngine create() {
    ProcessEngineConfigurationImpl configuration =
        (ProcessEngineConfigurationImpl)
            ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
    configuration
        .getProcessEnginePlugins()
        .add(new IncidentHandlerProcessEnginePlugin(loadNotificationProperties()));
    return configuration.buildProcessEngine();
  }

  public static Properties loadNotificationProperties() {
    Properties properties = new Properties();
    try (InputStream input =
        EngineConfigurationFactory.class.getResourceAsStream(
            "/incident-notifications.properties")) {
      if (input == null) {
        throw new IllegalStateException("Missing incident-notifications.properties.");
      }
      properties.load(input);
      return properties;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load incident notification settings.", exception);
    }
  }
}
