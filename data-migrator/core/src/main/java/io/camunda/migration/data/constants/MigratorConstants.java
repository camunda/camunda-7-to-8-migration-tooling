/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.constants;

public final class MigratorConstants {

  protected MigratorConstants() {}

  /**
   * Partition ID used for history data migration from Camunda 7 to Camunda 8.
   * Set to 4095 (maximum possible partition value) to ensure generated keys don't
   * collide with actual Zeebe partition keys during migration.
   */
  public static int C7_HISTORY_PARTITION_ID = 4095;

  public static final String LEGACY_ID_VAR_NAME = "legacyId";
  public static final String USER_TASK_ID = "userTaskId";
  public static final String C8_DEFAULT_TENANT = "<default>";

  /**
   * Generates a tree path for flow nodes in the format: processInstanceKey/elementInstanceKey
   * 
   * @param processInstanceKey the process instance key
   * @param elementInstanceKey the element instance key (flow node)
   * @return the tree path string
   */
  public static String generateTreePath(Long processInstanceKey, Long elementInstanceKey) {
    return processInstanceKey + "/" + elementInstanceKey;
  }
}
