/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.h2.jdbc.JdbcSQLSyntaxErrorException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class C7TablePrefixTest {

  /**
   * Verifies that the configured C7 table prefix (`MY_PREFIX_`) is applied to the engine tables:
   * with the prefix set the engine looks up `MY_PREFIX_ACT_GE_PROPERTY`, which does not exist, so
   * application startup fails.
   *
   * <p>The application is started in a self-contained way (rather than via {@code @SpringBootTest})
   * so the intentionally-failing context never enters Spring's test context cache and cannot leak
   * into other tests in the same JVM.
   */
  @Test
  public void shouldFailStartupSincePrefixedTableIsNotFound() {
    assertThatThrownBy(() -> new SpringApplicationBuilder(TestApp.class)
        .web(WebApplicationType.NONE)
        .properties(
            "camunda.migrator.c7.data-source.jdbc-url=jdbc:h2:mem:migrator-prefix",
            "camunda.migrator.c7.data-source.table-prefix=MY_PREFIX_")
        .run())
        .rootCause()
        .isInstanceOf(JdbcSQLSyntaxErrorException.class)
        .hasMessageContaining("Table \"MY_PREFIX_ACT_GE_PROPERTY\" not found");
  }

}
