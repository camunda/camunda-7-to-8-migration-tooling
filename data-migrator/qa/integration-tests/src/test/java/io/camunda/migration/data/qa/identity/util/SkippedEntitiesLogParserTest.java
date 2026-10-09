/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.util.EntitiesLogParserUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SkippedEntitiesLogParserTest {

  @Test
  public void shouldParseSkippedTenantIdsWhenEngineLogsAreInterleaved() {
    // given
    String output = """
        Previously skipped [Tenants]:
        2026-10-09T05:23:44.006Z  INFO 3173 --- [eam--1221571687] tc.camunda : ? - Committed new snapshot 0-0-3-0-0-888aeef4, isBoostrap: false
        tenantId-0-!~^
        tenantId-1-!~^
        tenantId-2-!~^
        tenantId-3-!~^
        tenantId-4-!~^
        tenantId-5-!~^
        tenantId-6-!~^
        tenantId-7-!~^
        tenantId-8-!~^
        tenantId-9-!~^
        """;

    // when
    var skippedEntities = EntitiesLogParserUtils.parseSkippedEntitiesOutput(output);

    // then
    assertThat(skippedEntities)
        .containsEntry(
            "Tenant",
            List.of(
                "tenantId-0-!~^",
                "tenantId-1-!~^",
                "tenantId-2-!~^",
                "tenantId-3-!~^",
                "tenantId-4-!~^",
                "tenantId-5-!~^",
                "tenantId-6-!~^",
                "tenantId-7-!~^",
                "tenantId-8-!~^",
                "tenantId-9-!~^"));
  }
}
