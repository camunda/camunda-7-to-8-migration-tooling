/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import io.camunda.migration.diagram.converter.conversion.Conversion;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ConversionFactory extends AbstractFactory<List<Conversion>> {
  private static final ConversionFactory INSTANCE = new ConversionFactory();

  public static ConversionFactory getInstance() {
    return INSTANCE;
  }

  @Override
  protected List<Conversion> createInstance() {
    ServiceLoader<Conversion> serviceLoader = ServiceLoader.load(Conversion.class);
    return StreamSupport.stream(serviceLoader.spliterator(), false).collect(Collectors.toList());
  }
}
