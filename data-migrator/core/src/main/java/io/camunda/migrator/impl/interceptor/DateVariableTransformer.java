/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.interceptor;

import static io.camunda.migrator.impl.logging.VariableServiceLogs.DATE_FORMAT_PATTERN;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.logConvertedDate;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.logConvertingDate;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.text.SimpleDateFormat;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.DateValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for Date variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Converts Date variables to ISO 8601 formatted strings compatible with Camunda 8.
 * This interceptor runs after other transformers to handle date conversion.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(20)  // Transform dates - runs after all other type transformers
@Component
public class DateVariableTransformer implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DateValue.class); // Only handle DateValue types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();
    DateValue dateValue = (DateValue) variable.getTypedValue(false);

    logConvertingDate(variable.getName());
    String formattedDate = new SimpleDateFormat(DATE_FORMAT_PATTERN).format(dateValue.getValue());
    logConvertedDate(variable.getName(), dateValue.getValue(), formattedDate);
    invocation.setVariableValue(formattedDate);
  }
}