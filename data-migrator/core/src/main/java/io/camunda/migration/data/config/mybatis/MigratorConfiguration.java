/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.mybatis;

import static io.camunda.migration.data.config.property.MigratorProperties.DataSource.C7;
import static io.camunda.migration.data.config.property.MigratorProperties.DataSource.C8;

import io.camunda.migration.data.config.property.MigratorProperties;
import java.util.Properties;
import javax.sql.DataSource;

import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MigratorConfiguration extends AbstractConfiguration {

  @Autowired
  @Qualifier("migratorDataSource")
  protected DataSource dataSource;

  @Bean
  @ConditionalOnProperty(prefix = MigratorProperties.PREFIX, name = "auto-ddl", havingValue = "true")
  public MultiTenantSpringLiquibase createMigratorSchema() {
    return createSchema(dataSource, configProperties.getTablePrefix(), "db/changelog/migrator/db.changelog-master.yaml",
        null);
  }

  @Bean
  public SqlSessionFactory migratorSqlSessionFactory() throws Exception {
    DbVendorProvider dbVendorProvider = new DbVendorProvider(getC7OrC8DbVendor());
    String dbVendor = dbVendorProvider.getDatabaseId(dataSource);
    Properties properties = loadPropertiesFile(dbVendor, "db/properties/" + dbVendor + ".properties");
    String tablePrefix = configProperties.getTablePrefix();
    return createSqlSessionFactory(dataSource, dbVendorProvider, properties, tablePrefix);
  }

  @Bean
  public MapperFactoryBean<IdKeyMapper> idKeyMapper(@Qualifier("migratorSqlSessionFactory") SqlSessionFactory migratorSqlSessionFactory) {
    return createMapperFactoryBean(migratorSqlSessionFactory, IdKeyMapper.class);
  }

  public String getC7OrC8DbVendor() {
    if (C7.equals(configProperties.getDataSource())) {
      if (configProperties.getC7() != null && configProperties.getC7().getDataSource() != null) {
        return configProperties.getC7().getDataSource().getVendor();
      }

    } else if (C8.equals(configProperties.getDataSource())) {
      if (configProperties.getC8() != null && configProperties.getC8().getDataSource() != null) {
        return configProperties.getC8().getDataSource().getVendor();
      }

    }

    return null;
  }

}
