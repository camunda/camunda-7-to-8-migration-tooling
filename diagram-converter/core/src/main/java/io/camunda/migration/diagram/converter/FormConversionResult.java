/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

/**
 * Result of converting and checking a Camunda form in one pass.
 *
 * @param convertedForm the converted form JSON
 * @param checkResult findings collected while converting the form
 */
public record FormConversionResult(String convertedForm, DiagramCheckResult checkResult) {}
