/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.util;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintUtils {

  protected static final Logger PRINTER = LoggerFactory.getLogger("PRINTER");

  public static void printSkippedInstancesHeader(long count, TYPE entityType) {
    String entityName = entityType.getDisplayName();
    String message = count > 0
        ? "Previously skipped [" + entityName + "s]:"
        : "No entities of type ["+ entityName +"] were skipped during previous migration";
    print(message);
  }

  public static void print(String message) {
    PRINTER.info(message);
  }
}