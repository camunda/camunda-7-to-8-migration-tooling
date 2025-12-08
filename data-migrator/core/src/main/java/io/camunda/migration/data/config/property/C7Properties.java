/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property;

public class C7Properties {

  protected DataSourceProperties dataSource;

  public DataSourceProperties getDataSource() {
    return dataSource;
  }

  public void setDataSource(DataSourceProperties dataSource) {
    this.dataSource = dataSource;
  }
}
