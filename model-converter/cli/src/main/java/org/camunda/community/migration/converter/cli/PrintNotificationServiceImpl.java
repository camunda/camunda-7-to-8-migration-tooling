/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.cli;

import static org.camunda.community.migration.converter.cli.ConvertCommand.*;

import org.camunda.community.migration.converter.NotificationService;

public class PrintNotificationServiceImpl implements NotificationService {
  @Override
  public void notify(Object object) {
    if (object instanceof RuntimeException) {
      throw (RuntimeException) object;
    } else {
      LOG_CLI.info("{}", object);
    }
  }
}
