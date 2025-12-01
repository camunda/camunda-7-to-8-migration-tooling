/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.util;

import io.camunda.client.api.command.ClientException;
import io.camunda.migrator.exception.HistoryMigratorException;
import io.camunda.migrator.exception.MigratorException;
import io.camunda.migrator.exception.RuntimeMigratorException;
import java.util.function.Supplier;
import org.apache.ibatis.exceptions.PersistenceException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionUtils {

  protected static final ThreadLocal<ExceptionContext> EXCEPTION_CONTEXT = ThreadLocal.withInitial(() -> null);

  public enum ExceptionContext {
    RUNTIME,
    HISTORY
  }

  protected static final Logger LOGGER = LoggerFactory.getLogger(ExceptionUtils.class);

  public static void setContext(ExceptionContext context) {
    EXCEPTION_CONTEXT.set(context);
  }

  public static void clearContext() {
    EXCEPTION_CONTEXT.remove();
  }

  public static <T> T callApi(Supplier<T> supplier) {
    return callApi(supplier, "Error occurred: shutting down Data Migrator gracefully.");
  }

  /**
   * Wraps {@link ClientException}, {@link ProcessEngineException}, and {@link PersistenceException} into {@link RuntimeMigratorException}.
   */
  public static <T> T callApi(Supplier<T> supplier, String message) {
    try {
      return supplier.get();
    } catch (ClientException | ProcessEngineException | PersistenceException e) {
      throw wrapException(message, e);
    }
  }

  public static void callApi(Runnable runnable) {
    callApi(runnable, "Error occurred: shutting down Data Migrator gracefully.");
  }

  /**
   * Wraps {@link ClientException}, {@link ProcessEngineException}, and {@link PersistenceException} into {@link RuntimeMigratorException}.
   */
  public static void callApi(Runnable runnable, String message) {
    try {
      runnable.run();
    } catch (ClientException | ProcessEngineException | PersistenceException e) {
      throw wrapException(message, e);
    }
  }

  public static MigratorException wrapException(String message, Exception e) {
    ExceptionContext context = EXCEPTION_CONTEXT.get();
    MigratorException exception;

    if (context == ExceptionContext.HISTORY) {
      exception = new HistoryMigratorException(message, e);
    } else {
      // Default to RuntimeMigratorException
      exception = new RuntimeMigratorException(message, e);
    }

    LOGGER.error(message, exception);
    return exception;
  }

}
