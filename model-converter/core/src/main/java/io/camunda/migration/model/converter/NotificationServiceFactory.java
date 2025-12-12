/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class NotificationServiceFactory extends AbstractFactory<NotificationService> {
  private static final NotificationServiceFactory INSTANCE = new NotificationServiceFactory();

  public static NotificationServiceFactory getInstance() {
    return INSTANCE;
  }

  @Override
  protected NotificationService createInstance() {
    return new ComposedNotificationService(
        StreamSupport.stream(ServiceLoader.load(NotificationService.class).spliterator(), false)
            .collect(Collectors.toList()));
  }

  public static class ComposedNotificationService implements NotificationService {
    private final List<NotificationService> notificationServices;

    public ComposedNotificationService(List<NotificationService> notificationServices) {
      this.notificationServices = notificationServices;
    }

    @Override
    public void notify(Object object) {
      notificationServices.forEach(s -> s.notify(object));
    }
  }
}
