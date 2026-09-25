/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.incident;

import org.camunda.bpm.engine.impl.incident.IncidentContext;
import org.camunda.bpm.engine.impl.incident.IncidentHandler;
import org.camunda.bpm.engine.runtime.Incident;

public final class ErrorNotificationIncidentHandler implements IncidentHandler {

  protected final EmailNotificationClient notificationClient;

  public ErrorNotificationIncidentHandler(EmailNotificationClient notificationClient) {
    this.notificationClient = notificationClient;
  }

  @Override
  public String getIncidentHandlerType() {
    return Incident.FAILED_JOB_HANDLER_TYPE;
  }

  @Override
  public Incident handleIncident(IncidentContext context, String message) {
    notificationClient.send(context, message);
    // The default handler stores the incident. This handler only sends the notification.
    return null;
  }

  @Override
  public void resolveIncident(IncidentContext context) {}

  @Override
  public void deleteIncident(IncidentContext context) {}
}
