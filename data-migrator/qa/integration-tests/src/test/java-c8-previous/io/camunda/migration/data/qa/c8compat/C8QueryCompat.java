/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.c8compat;

import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.ProcessDefinitionQuery;

/**
 * Version-specific test query helpers for the previous Camunda 8 version.
 *
 * <p>Lives under {@code src/test/java-c8-previous} and is added to the test source set by the
 * {@code c8-previous-api} Maven profile (activated when {@code -Dversion.camunda-8} matches the
 * previous version pin). A parallel implementation exists under {@code src/test/java-c8-current}
 * for the current Camunda 8 version.
 *
 * <p>In 8.9 {@code ProcessDefinitionQuery.Builder} has no {@code resultConfig} — BPMN XML is
 * included by default. {@code FlowNodeInstanceFilter.Builder.flowNodeIds} accepts a varargs
 * {@code String[]} directly.
 */
public final class C8QueryCompat {

  private C8QueryCompat() {}

  public static ProcessDefinitionQuery processDefinitionQueryWithBpmnXml(
      final String prefixedProcessDefinitionId) {
    return ProcessDefinitionQuery.of(
        queryBuilder ->
            queryBuilder.filter(
                filterBuilder -> filterBuilder.processDefinitionIds(prefixedProcessDefinitionId)));
  }

  public static FlowNodeInstanceQuery flowNodeInstanceQueryByIds(final String... flowNodeIds) {
    return FlowNodeInstanceQuery.of(
        queryBuilder ->
            queryBuilder.filter(filterBuilder -> filterBuilder.flowNodeIds(flowNodeIds)));
  }
}
