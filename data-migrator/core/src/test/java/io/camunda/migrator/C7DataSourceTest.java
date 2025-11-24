/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.c7.data-source.jdbc-url=jdbc:h2:mem:my-test;DB_CLOSE_DELAY=-1",
    "camunda.migrator.c7.data-source.username=abc",
    "camunda.migrator.c7.data-source.password=xyz",
    "camunda.migrator.c7.data-source.driverClassName=org.springframework.jdbc.datasource.SimpleDriverDataSource",
})
@SpringBootTest
public class C7DataSourceTest {

  @Autowired
  @Qualifier("c7DataSource")
  protected DataSource c7DataSource;

  @Autowired
  @Qualifier("migratorDataSource")
  protected DataSource migratorDataSource;

  @Autowired(required = false)
  @Qualifier("c8DataSource")
  protected DataSource c8DataSource;

  @Test
  public void shouldConfigureC7DataSource() {
    assertThat(c8DataSource).isNull();
    assertThat(c7DataSource).isEqualTo(migratorDataSource);
    assertThat(c7DataSource).isInstanceOf(HikariDataSource.class)
        .extracting("username", "password", "jdbcUrl", "driverClassName")
        .containsExactly("abc", "xyz", "jdbc:h2:mem:my-test;DB_CLOSE_DELAY=-1", SimpleDriverDataSource.class.getName());
  }

}
