/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.mybatis;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.logging.ConfigurationLogs;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class AbstractConfiguration {

  @Autowired
  protected MigratorProperties configProperties;

  protected Properties loadPropertiesFile(String databaseId, String file) throws IOException {
    Properties properties = new Properties();

    try (final var propertiesInputStream = getClass().getClassLoader().getResourceAsStream(file)) {
      if (propertiesInputStream != null) {
        properties.load(propertiesInputStream);
      } else {
        throw new IllegalArgumentException("No vendor properties found for databaseId " + databaseId);
      }
    }
    return properties;
  }

  protected SqlSessionFactory createSqlSessionFactory(DataSource dataSource,
                                                      DatabaseIdProvider databaseIdProvider,
                                                      Properties databaseProperties,
                                                      String tablePrefix) throws Exception {
    var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);

    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    factoryBean.setDatabaseIdProvider(databaseIdProvider);
    factoryBean.addMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    Properties p = new Properties();

    p.put("prefix", StringUtils.trimToEmpty(tablePrefix));
    p.putAll(databaseProperties);
    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }

  protected  <T> MapperFactoryBean<T> createMapperFactoryBean(final SqlSessionFactory sqlSessionFactory,
                                                           final Class<T> clazz) {
    final MapperFactoryBean<T> factoryBean = new MapperFactoryBean<>(clazz);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  protected MultiTenantSpringLiquibase createSchema(DataSource dataSource,
                                                    String tablePrefix,
                                                    String changeLogFile,
                                                    String userCharColumnSize) {
    String prefix = StringUtils.trimToEmpty(tablePrefix);
    ConfigurationLogs.logCreatingTableSchema(changeLogFile, prefix);

    var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    moduleConfig.setDatabaseChangeLogTable(prefix + "DATABASECHANGELOG");
    moduleConfig.setDatabaseChangeLogLockTable(prefix + "DATABASECHANGELOGLOCK");
    if (StringUtils.isEmpty(userCharColumnSize)) {
      moduleConfig.setParameters(Map.of("prefix", prefix));
    } else {
      moduleConfig.setParameters(Map.of("prefix", prefix, "userCharColumnSize", userCharColumnSize));
    }
    moduleConfig.setChangeLog(changeLogFile);

    return moduleConfig;
  }

}
