/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.webapp.properties;

import io.camunda.migration.model.converter.webapp.SlackProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("notification")
@Configuration
public class NotificationProperties {
  @NestedConfigurationProperty private SlackProperties slack;

  public SlackProperties getSlack() {
    return slack;
  }

  public void setSlack(SlackProperties slack) {
    this.slack = slack;
  }
}
