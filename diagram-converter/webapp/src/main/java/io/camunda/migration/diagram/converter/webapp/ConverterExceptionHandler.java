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
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@ControllerAdvice
public class ConverterExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ConverterExceptionHandler.class);

  @ExceptionHandler(MultipartException.class)
  public void handleMultipartException(MultipartException ex, HttpServletResponse response)
      throws IOException {
    Throwable rootCause = getRootCause(ex);

    if (rootCause instanceof FileCountLimitExceededException fileCountEx) {
      LOG.warn("File count limit exceeded: {}", rootCause.getMessage());
      writeJsonError(
          response,
          HttpStatus.PAYLOAD_TOO_LARGE,
          "FILE_COUNT_LIMIT_EXCEEDED",
          "maxPartCount",
          fileCountEx.getLimit());
      return;
    }

    long maxUploadSize = -1;
    if (rootCause instanceof FileSizeLimitExceededException exception) {
      maxUploadSize = exception.getPermittedSize();
    } else if (rootCause instanceof SizeLimitExceededException exception) {
      maxUploadSize = exception.getPermittedSize();
    } else if (ex instanceof MaxUploadSizeExceededException exception) {
      maxUploadSize = exception.getMaxUploadSize();
    } else if (rootCause instanceof MaxUploadSizeExceededException exception) {
      maxUploadSize = exception.getMaxUploadSize();
    }

    if (ex instanceof MaxUploadSizeExceededException || maxUploadSize >= 0) {
      LOG.warn("Multipart upload size limit exceeded: {}", ex.getMessage());
      writeJsonError(
          response,
          HttpStatus.PAYLOAD_TOO_LARGE,
          "FILE_SIZE_LIMIT_EXCEEDED",
          "maxUploadSize",
          maxUploadSize);
      return;
    }

    LOG.error("Multipart request processing failed", ex);
    writeJsonError(response, HttpStatus.BAD_REQUEST, "MULTIPART_ERROR", "maxPartCount", -1);
  }

  private void writeJsonError(
      HttpServletResponse response,
      HttpStatus status,
      String errorCode,
      String limitName,
      long limit)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    StringBuilder json =
        new StringBuilder("{\"errorCode\":\"")
            .append(errorCode)
            .append("\",\"status\":")
            .append(status.value());
    if (limitName != null) {
      json.append(",\"").append(limitName).append("\":").append(limit);
    }
    response.getWriter().write(json.append("}").toString());
  }

  private Throwable getRootCause(Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause;
  }
}
