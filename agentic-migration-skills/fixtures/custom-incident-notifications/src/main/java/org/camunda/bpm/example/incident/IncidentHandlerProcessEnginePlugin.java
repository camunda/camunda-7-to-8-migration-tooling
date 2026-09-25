/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.incident.IncidentHandler;

public final class IncidentHandlerProcessEnginePlugin implements ProcessEnginePlugin {

  protected final Properties notificationProperties;

  public IncidentHandlerProcessEnginePlugin(Properties notificationProperties) {
    this.notificationProperties =
        Objects.requireNonNull(notificationProperties, "notificationProperties");
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    processEngineConfiguration.setCompositeIncidentHandlersEnabled(true);
    List<IncidentHandler> handlers = processEngineConfiguration.getCustomIncidentHandlers();
    if (handlers == null) {
      handlers = new ArrayList<>();
      processEngineConfiguration.setCustomIncidentHandlers(handlers);
    }
    handlers.add(
        new ErrorNotificationIncidentHandler(
            new EmailNotificationClient(notificationProperties)));
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {}

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {}
}
