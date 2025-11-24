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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.c7.data-source.jdbc-url=jdbc:h2:mem:c7;DB_CLOSE_DELAY=-1",
    "camunda.migrator.c7.data-source.username=c7-abc",
    "camunda.migrator.c7.data-source.password=c7-xyz",
    "camunda.migrator.c7.data-source.driverClassName=org.springframework.jdbc.datasource.SimpleDriverDataSource",
    "camunda.migrator.c8.data-source.jdbc-url=jdbc:h2:mem:c8;DB_CLOSE_DELAY=-1",
    "camunda.migrator.c8.data-source.username=c8-abc",
    "camunda.migrator.c8.data-source.password=c8-xyz",
    "camunda.migrator.c8.data-source.driverClassName=org.h2.jdbcx.JdbcDataSource",
})
@SpringBootTest
public class C7AndC8DataSourceTest {

  @Autowired
  @Qualifier("c7DataSource")
  protected DataSource c7DataSource;

  @Autowired
  @Qualifier("migratorDataSource")
  protected DataSource migratorDataSource;

  @Autowired
  @Qualifier("c8DataSource")
  protected DataSource c8DataSource;

  @Test
  public void shouldConfigureDataSources() {
    assertThat(c7DataSource).isEqualTo(migratorDataSource);
    assertThat(c7DataSource).isInstanceOf(HikariDataSource.class)
        .extracting("username", "password", "jdbcUrl", "driverClassName")
        .containsExactly("c7-abc", "c7-xyz", "jdbc:h2:mem:c7;DB_CLOSE_DELAY=-1", SimpleDriverDataSource.class.getName());
    assertThat(c8DataSource).isInstanceOf(HikariDataSource.class)
        .extracting("username", "password", "jdbcUrl", "driverClassName")
        .containsExactly("c8-abc", "c8-xyz", "jdbc:h2:mem:c8;DB_CLOSE_DELAY=-1", JdbcDataSource.class.getName());
  }

}
