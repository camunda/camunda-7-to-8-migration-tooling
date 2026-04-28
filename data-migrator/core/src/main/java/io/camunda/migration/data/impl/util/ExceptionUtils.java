/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.util;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.migration.data.exception.HistoryMigratorException;
import org.apache.hc.client5.http.HttpHostConnectException;
import io.camunda.migration.data.exception.IdentityMigratorException;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.exception.RuntimeMigratorException;
import java.util.function.Supplier;
import org.apache.ibatis.exceptions.PersistenceException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionUtils {

  protected static final ThreadLocal<ExceptionContext> EXCEPTION_CONTEXT = ThreadLocal.withInitial(() -> null);

  public enum ExceptionContext {
    RUNTIME,
    HISTORY,
    IDENTITY
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
   * Wraps {@link ClientException}, {@link ProcessEngineException}, {@link IllegalArgumentException} and {@link PersistenceException} into {@link RuntimeMigratorException}.
   */
  public static <T> T callApi(Supplier<T> supplier, String message) {
    try {
      return supplier.get();
    } catch (ClientException | ProcessEngineException | IllegalArgumentException | PersistenceException e) {
      throw wrapException(message, e);
    }
  }

  public static void callApi(Runnable runnable) {
    callApi(runnable, "Error occurred: shutting down Data Migrator gracefully.");
  }

  /**
   * Wraps {@link ClientException}, {@link ProcessEngineException}, {@link IllegalArgumentException} and {@link PersistenceException} into {@link RuntimeMigratorException}.
   */
  public static void callApi(Runnable runnable, String message) {
    try {
      runnable.run();
    } catch (ClientException | ProcessEngineException | IllegalArgumentException | PersistenceException e) {
      throw wrapException(message, e);
    }
  }

  public static MigratorException wrapException(String message, Exception e) {
    ExceptionContext context = EXCEPTION_CONTEXT.get();
    MigratorException exception;

    boolean skipErrorLog = false;
    if (context == ExceptionContext.HISTORY) {
      exception = new HistoryMigratorException(message, e);
    } else if (context == ExceptionContext.IDENTITY) {
      if (e instanceof ProblemException pe) {
        skipErrorLog = true;
        exception = new IdentityMigratorException(pe.details().getDetail(), e);
      } else  {
        exception = new IdentityMigratorException(message, e);
      }
    } else if (context == ExceptionContext.RUNTIME) {
      // Default to RuntimeMigratorException
      exception = new RuntimeMigratorException(message, e);
    } else {
      exception = new MigratorException(message, e);
    }

    if (!skipErrorLog) {
      LOGGER.error(message, exception);
    }
    return exception;
  }

  /**
   * Traverses the full cause chain to check whether C8 is offline, indicated by an
   * {@link HttpHostConnectException} (connection refused).
   */
  public static void rethrowIfC8Offline(MigratorException e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof HttpHostConnectException) {
        throw e;
      }
      cause = cause.getCause();
    }
  }

}
