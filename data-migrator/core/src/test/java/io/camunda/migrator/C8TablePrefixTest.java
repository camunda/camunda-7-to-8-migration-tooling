/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.c8.data-source.auto-ddl=true",
    "camunda.migrator.c8.data-source.table-prefix=MY_PREFIX_",
})
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
public class C8TablePrefixTest {

  @Test
  public void shouldCreateC8RdbmsTableSchemaWithTablePrefix(CapturedOutput output) {
    assertThat(output)
        .contains("Creating table schema with Liquibase change log file "
            + "'db/changelog/rdbms-exporter/changelog-master.xml' with table prefix 'MY_PREFIX_'");
  }

}
