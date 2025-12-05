/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.MigratorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "camunda.migrator.save-skip-reason=false")
class SaveSkipReasonPropertyDisabledTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Test
  public void shouldHaveSaveSkipReasonAsFalse() {
    assertThat(migratorProperties.getSaveSkipReason()).isFalse();
  }
}
