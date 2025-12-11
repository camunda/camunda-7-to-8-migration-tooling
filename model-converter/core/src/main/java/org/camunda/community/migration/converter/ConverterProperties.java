/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter;

public interface ConverterProperties {

  String getScriptHeader();

  String getResultVariableHeader();

  String getDefaultJobType();

  String getScriptJobType();

  String getResourceHeader();

  String getScriptFormatHeader();

  String getPlatformVersion();

  Boolean getAppendDocumentation();

  Boolean getKeepJobTypeBlank();

  Boolean getAlwaysUseDefaultJobType();

  Boolean getAppendElements();

  Boolean getAddDataMigrationExecutionListener();

  String getDataMigrationExecutionListenerJobType();
}
