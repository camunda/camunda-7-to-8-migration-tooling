/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import io.camunda.migrator.config.property.MigratorProperties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "camunda.migrator.page-size=2" })
class PageSizeConfigurationPropertyTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Test
  public void shouldSetPageSize() {

    Assertions.assertThat(migratorProperties.getPageSize()).isEqualTo(2);
  }

}