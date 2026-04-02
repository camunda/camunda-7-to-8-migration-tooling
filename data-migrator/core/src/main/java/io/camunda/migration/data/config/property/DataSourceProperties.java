/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property;

import com.zaxxer.hikari.HikariConfig;
import io.camunda.migration.data.impl.logging.ConfigurationLogs;

public class DataSourceProperties extends HikariConfig {

  protected Boolean autoDdl;
  protected String tablePrefix;
  protected String vendor;

  public Boolean getAutoDdl() {
    return autoDdl;
  }

  public void setAutoDdl(Boolean autoDdl) {
    this.autoDdl = autoDdl;
  }

  public String getTablePrefix() {
    return tablePrefix;
  }

  public void setTablePrefix(String tablePrefix) {
    this.tablePrefix = tablePrefix;
  }

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  @Override
  public void setDriverClassName(String driverClassName) {
    try {
      super.setDriverClassName(driverClassName);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof ClassNotFoundException || e.getCause().getCause() instanceof ClassNotFoundException) {
        ConfigurationLogs.logInvalidJdbcDriver(driverClassName);
      }
      throw e;
    }
  }

}
