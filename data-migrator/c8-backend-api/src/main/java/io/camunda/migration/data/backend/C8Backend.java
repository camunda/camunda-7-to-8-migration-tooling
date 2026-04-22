/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.backend;

/**
 * Marker SPI fulfilled by exactly one {@code c8-backend-*} implementation module on the
 * runtime classpath. The impl also registers the version-specific
 * {@code io.camunda.db.rdbms.RdbmsService} Spring bean that {@code core} consumes — that
 * construction call is the version-sensitive surface, not anything on this interface.
 *
 * <p>Kept intentionally minimal: richer operations would force this module to depend on
 * specific {@code io.camunda.*} types and re-introduce the version coupling this split
 * exists to break.
 */
public interface C8Backend {

  /** Human-readable C8 minor this backend targets, e.g. {@code "8.10"} or {@code "8.9"}. */
  String supportedVersion();
}
