/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter;

public class DiagramConverterFactory extends AbstractFactory<DiagramConverter> {
  private static final DiagramConverterFactory INSTANCE = new DiagramConverterFactory();
  private final DomElementVisitorFactory domElementVisitorFactory =
      DomElementVisitorFactory.getInstance();
  private final ConversionFactory conversionFactory = ConversionFactory.getInstance();
  private final NotificationServiceFactory notificationServiceFactory =
      NotificationServiceFactory.getInstance();

  public static DiagramConverterFactory getInstance() {
    return INSTANCE;
  }

  @Override
  protected DiagramConverter createInstance() {
    return new DiagramConverter(
        domElementVisitorFactory.get(), conversionFactory.get(), notificationServiceFactory.get());
  }

  public DomElementVisitorFactory getDomElementVisitorFactory() {
    return domElementVisitorFactory;
  }

  public ConversionFactory getConversionFactory() {
    return conversionFactory;
  }

  public NotificationServiceFactory getNotificationServiceFactory() {
    return notificationServiceFactory;
  }
}
