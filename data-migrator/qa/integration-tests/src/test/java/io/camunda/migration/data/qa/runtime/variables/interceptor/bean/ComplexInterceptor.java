/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.runtime.variables.interceptor.bean;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(102)
@Component
@Profile("programmatic")
public class ComplexInterceptor implements VariableInterceptor {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ComplexInterceptor.class);

  protected static final String INTERCEPTOR_START_MESSAGE = "Start {} execution for variable: {}";
  protected static final String INTERCEPTOR_END_MESSAGE = "End {} execution for variable: {}";
  protected static final String HELLO_FROM_INTERCEPTOR_MESSAGE = "Hello from interceptor";
  protected static final String BYE_FROM_INTERCEPTOR_MESSAGE = "Bye from interceptor";
  protected static final String EXPECTED_EXCEPTION_MESSAGE = "Expected exception from Interceptor";

  @Override
  public void execute(VariableInvocation invocation) {
    LOGGER.debug(INTERCEPTOR_START_MESSAGE, ComplexInterceptor.class,
        invocation.getC7Variable().getName());
    if (invocation.getC7Variable().getName().equals("varIntercept")) {
      LOGGER.info(HELLO_FROM_INTERCEPTOR_MESSAGE);
      invocation.setVariableValue("Hello");
    }

    if (invocation.getC7Variable().getName().equals("exFlag")) {
      if (Boolean.parseBoolean(invocation.getC7Variable().getValue().toString())) {
        throw new RuntimeException(EXPECTED_EXCEPTION_MESSAGE);
      } else {
        LOGGER.info(BYE_FROM_INTERCEPTOR_MESSAGE);
      }
    }
    LOGGER.debug(INTERCEPTOR_END_MESSAGE, ComplexInterceptor.class,
        invocation.getC7Variable().getName());
  }
}