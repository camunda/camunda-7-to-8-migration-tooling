/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.persistence;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.apache.ibatis.annotations.Param;

public interface IdKeyMapper {

  enum TYPE {
    HISTORY_PROCESS_DEFINITION("Historic Process Definition"),
    HISTORY_PROCESS_INSTANCE("Historic Process Instance"),
    HISTORY_INCIDENT("Historic Incident"),
    HISTORY_VARIABLE("Historic Variable"),
    HISTORY_USER_TASK("Historic User Task"),
    HISTORY_FLOW_NODE("Historic Flow Node"),
    HISTORY_DECISION_INSTANCE("Historic Decision Instance"),
    HISTORY_DECISION_INSTANCE_INPUT("Historic Decision Instance Input"),
    HISTORY_DECISION_INSTANCE_OUTPUT("Historic Decision Instance Output"),
    HISTORY_DECISION_DEFINITION("Historic Decision Definition"),
    HISTORY_DECISION_REQUIREMENT("Historic Decision Requirement"),

    RUNTIME_PROCESS_INSTANCE("Process Instance");

    protected final String displayName;

    TYPE(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  Set<TYPE> HISTORY_TYPES = Arrays.stream(TYPE.values())
      .filter(type -> type.name().startsWith("HISTORY"))
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(TYPE.class)));

  /**
   * Returns the names of all history-related entity types as strings.
   */
  static Set<String> getHistoryTypeNames() {
    return HISTORY_TYPES.stream().map(Enum::name).collect(Collectors.toSet());
  }

  /**
   * Returns all history-related entity types as enum values.
   */
  static Set<TYPE> getHistoryTypes() {
    return HISTORY_TYPES;
  }

  boolean checkExistsByC7IdAndType(@Param("type") TYPE type, @Param("c7Id") String c7Id);

  boolean checkHasC8KeyByC7IdAndType(@Param("type") TYPE type, @Param("c7Id") String c7Id);

  Date findLatestCreateTimeByType(TYPE type);

  Long findC8KeyByC7IdAndType(@Param("c7Id") String id, @Param("type") TYPE type);

  void insert(IdKeyDbModel idKeyDbModel);

  void insertBatch(List<IdKeyDbModel> idKeyDbModels);

  List<IdKeyDbModel> findSkippedByType(@Param("type") TYPE type, @Param("offset") int offset, @Param("limit") int limit);

  List<IdKeyDbModel> findMigratedByType(@Param("type") TYPE type, @Param("offset") int offset, @Param("limit") int limit);

  long countSkippedByType(@Param("type") TYPE type);

  long countSkipped();

  List<String> findAllC7Ids();

  void updateC8KeyByC7IdAndType(IdKeyDbModel idKeyDbModel);

  void updateSkipReason(IdKeyDbModel idKeyDbModel);

  void deleteByC7Id(String c7Id);
}
