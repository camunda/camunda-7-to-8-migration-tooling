/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator;

import static io.camunda.migrator.C7TablePrefixTest.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbc.JdbcSQLSyntaxErrorException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.c7.data-source.jdbc-url=jdbc:h2:mem:migrator-prefix;DB_CLOSE_DELAY=-1",
    "camunda.migrator.c7.data-source.table-prefix=MY_PREFIX_"
})
@SpringBootTest
@TestExecutionListeners(CustomTestExecutionListener.class)
public class C7TablePrefixTest {

  /**
   * This test verifies that engine tables are prefix with `MY_PREFIX_`. See `CustomTestExecutionListener` for validation logic.
   */
  @Test
  public void shouldThrowExceptionSincePrefixedTableIsNotFound() {
  }

  static class CustomTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) {
      try {
        // Attempt to load application context
        testContext.getApplicationContext();
      } catch (IllegalStateException e) {
        Throwable nestedCause = findNestedCause(e, JdbcSQLSyntaxErrorException.class);
        assertThat(nestedCause).isNotNull();
        assertThat(nestedCause).hasMessageContaining("Table \"MY_PREFIX_ACT_GE_PROPERTY\" not found");
      }
    }

    protected Throwable findNestedCause(Throwable throwable, Class<? extends Throwable> targetType) {
      Throwable current = throwable;
      while (current != null && !targetType.isInstance(current)) {
        current = current.getCause();
      }
      return targetType.isInstance(current) ? current : null;
    }

  }

}
