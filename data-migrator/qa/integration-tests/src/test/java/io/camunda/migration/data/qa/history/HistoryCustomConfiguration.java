/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migration.data.config.C8DataSourceConfigured;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HistoryCustomConfiguration {

  @Bean
  @Conditional(C8DataSourceConfigured.class)
  public RdbmsPurger rdbmsPurger(
      PurgeMapper purgeMapper,
      VendorDatabaseProperties vendorDatabaseProperties) {
    return new RdbmsPurger(purgeMapper, vendorDatabaseProperties);
  }

}
