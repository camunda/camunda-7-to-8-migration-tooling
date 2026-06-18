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
import java.util.Arrays;

/**
 * Version-specific test query helpers for the current Camunda 8 version.
 *
 * <p>Lives under {@code src/test/java-c8-current} and is added to the test source set by the
 * {@code c8-current-api} Maven profile. A parallel implementation exists under
 * {@code src/test/java-c8-previous} for the previous Camunda 8 version.
 */
public final class C8QueryCompat {

  private C8QueryCompat() {}

  public static ProcessDefinitionQuery processDefinitionQueryWithBpmnXml(
      final String prefixedProcessDefinitionId) {
    return ProcessDefinitionQuery.of(
        queryBuilder ->
            queryBuilder
                .filter(filterBuilder -> filterBuilder.processDefinitionIds(prefixedProcessDefinitionId))
                .resultConfig(b -> b.includeXml(true)));
  }

  public static FlowNodeInstanceQuery flowNodeInstanceQueryByIds(final String... flowNodeIds) {
    if (flowNodeIds.length == 0) {
      throw new IllegalArgumentException("At least one flowNodeId is required");
    }
    final String first = flowNodeIds[0];
    final String[] rest = Arrays.copyOfRange(flowNodeIds, 1, flowNodeIds.length);
    return FlowNodeInstanceQuery.of(
        queryBuilder ->
            queryBuilder.filter(filterBuilder -> filterBuilder.flowNodeIds(first, rest)));
  }
}
