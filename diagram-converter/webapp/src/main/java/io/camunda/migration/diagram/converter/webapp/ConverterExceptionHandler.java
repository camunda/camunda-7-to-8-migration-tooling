/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.webapp;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;

@ControllerAdvice
public class ConverterExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ConverterExceptionHandler.class);

  @Value("${server.tomcat.max-part-count:50}")
  private int maxPartCount;

  @ExceptionHandler(MultipartException.class)
  public void handleMultipartException(MultipartException ex, HttpServletResponse response)
      throws IOException {
    Throwable rootCause = getRootCause(ex);

    if (rootCause instanceof FileCountLimitExceededException) {
      LOG.warn("File count limit exceeded: {}", rootCause.getMessage());
      writeJsonError(
          response, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_COUNT_LIMIT_EXCEEDED", maxPartCount);
      return;
    }

    LOG.error("Multipart request processing failed", ex);
    writeJsonError(response, HttpStatus.BAD_REQUEST, "MULTIPART_ERROR", -1);
  }

  private void writeJsonError(
      HttpServletResponse response, HttpStatus status, String errorCode, int limit)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    String json =
        "{\"errorCode\":\""
            + errorCode
            + "\",\"status\":"
            + status.value()
            + ",\"limit\":"
            + limit
            + "}";
    response.getWriter().write(json);
  }

  private Throwable getRootCause(Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause;
  }
}
