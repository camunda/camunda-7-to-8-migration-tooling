/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.mybatis;

import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;

/**
 * Adds migration-tooling MyBatis compatibility before Camunda 7 executes mapped queries.
 */
public class MigratorSpringProcessEngineConfiguration extends SpringProcessEngineConfiguration {

  @Override
  protected void initSqlSessionFactory() {
    super.initSqlSessionFactory();

    var configuration = getSqlSessionFactory().getConfiguration();
    var objectWrapperFactory = configuration.getObjectWrapperFactory();
    if (!(objectWrapperFactory instanceof Camunda7QueryObjectWrapperFactory)) {
      configuration.setObjectWrapperFactory(
          new Camunda7QueryObjectWrapperFactory(objectWrapperFactory));
    }
  }
}
