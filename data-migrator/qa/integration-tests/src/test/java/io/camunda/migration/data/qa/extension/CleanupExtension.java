/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.extension;

import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.extension.Extension;

/**
 * JUnit extension for querying history cleanup dates from C8 database tables.
 *
 * <p>This extension provides helper methods to verify HISTORY_CLEANUP_DATE column values
 * in C8 tables during whitebox testing. Since cleanup dates are not exposed via public API,
 * direct SQL queries are needed to verify correct cleanup date calculation (endDate + TTL).</p>
 *
 * <p>Usage in tests:</p>
 * <pre>
 * {@literal @}RegisterExtension
 * RdbmsQueryExtension rdbmsQuery = new RdbmsQueryExtension();
 *
 * {@literal @}RegisterExtension
 * CleanupExtension cleanup = new CleanupExtension(rdbmsQuery);
 *
 * // Query cleanup date for a process instance
 * OffsetDateTime cleanupDate = cleanup.getProcessInstanceCleanupDate(processInstanceKey);
 * assertThat(cleanupDate).isEqualTo(endDate.plus(Duration.ofDays(30)));
 * </pre>
 */
public class CleanupExtension implements Extension {

  protected final RdbmsQueryExtension rdbmsQuery;

  /**
   * Creates a CleanupExtension with the specified RdbmsQueryExtension.
   *
   * @param rdbmsQuery the RdbmsQueryExtension to use for database queries
   */
  public CleanupExtension(RdbmsQueryExtension rdbmsQuery) {
    this.rdbmsQuery = rdbmsQuery;
  }

  protected static OffsetDateTime mapRow(ResultSet rs, int rowNum) throws SQLException {
    return rs.getObject("HISTORY_CLEANUP_DATE", OffsetDateTime.class);
  }

  protected String getSql(String tableName, String keyColumn) {
    return String.format("SELECT HISTORY_CLEANUP_DATE FROM %s WHERE %s = ?", tableName, keyColumn);
  }

  /**
   * Generic method to query cleanup date information from any C8 table.
   *
   * @param tableName the name of the C8 table
   * @param keyColumn the key column name
   * @param value the value to search for
   * @return the history cleanup date, or null if not set
   */
  public OffsetDateTime queryCleanupDate(String tableName, String keyColumn, Object value) {
    return rdbmsQuery.queryForObject(getSql(tableName, keyColumn), CleanupExtension::mapRow, value);
  }

  /**
   * Generic method to query multiple cleanup date records from any C8 table.
   *
   * @param tableName the name of the C8 table
   * @param filterColumn the filter column name
   * @param filterValue the filter value
   * @return list of history cleanup dates
   */
  public List<OffsetDateTime> queryCleanupDates(String tableName, String filterColumn, Long filterValue) {
    return rdbmsQuery.query(getSql(tableName, filterColumn), CleanupExtension::mapRow, filterValue);
  }

  /**
   * Query cleanup date for a process instance.
   *
   * @param processInstanceKey the process instance key
   * @return the history cleanup date, or null if not set
   */
  public OffsetDateTime getProcessInstanceCleanupDate(Long processInstanceKey) {
    return queryCleanupDate("PROCESS_INSTANCE", "PROCESS_INSTANCE_KEY", processInstanceKey);
  }

  /**
   * Query cleanup date for a flow node instance.
   *
   * @param flowNodeInstanceKey the flow node instance key
   * @return the history cleanup date, or null if not set
   */
  public OffsetDateTime getFlowNodeCleanupDate(Long flowNodeInstanceKey) {
    return queryCleanupDate("FLOW_NODE_INSTANCE", "FLOW_NODE_INSTANCE_KEY", flowNodeInstanceKey);
  }

  /**
   * Query cleanup date for a user task.
   * Note: For user tasks, endDate represents COMPLETION_DATE.
   *
   * @param userTaskKey the user task key
   * @return the history cleanup date, or null if not set
   */
  public OffsetDateTime getUserTaskCleanupDate(Long userTaskKey) {
    return queryCleanupDate("USER_TASK", "USER_TASK_KEY", userTaskKey);
  }

  /**
   * Query cleanup date for a decision instance.
   * Note: For decision instances, endDate represents EVALUATION_DATE.
   *
   * @param decisionInstanceDefinitionId the decision definition id
   * @return the history cleanup date, or null if not set
   */
  public OffsetDateTime getDecisionInstanceCleanupDate(String decisionInstanceDefinitionId) {
    return queryCleanupDate("DECISION_INSTANCE", "DECISION_DEFINITION_ID", prefixDefinitionId(decisionInstanceDefinitionId));
  }

  /**
   * Query cleanup date for a decision instance.
   * Note: For decision instances, endDate represents EVALUATION_DATE.
   *
   * @param decisionInstanceKey the decision instance key
   * @return the history cleanup date, or null if not set
   */
  public List<OffsetDateTime> getDecisionInstanceCleanupDates(Long decisionInstanceKey) {
    return queryCleanupDates("DECISION_INSTANCE", "DECISION_INSTANCE_KEY", decisionInstanceKey);
  }

  /**
   * Query cleanup dates for all variables of a process instance.
   * Variables don't have an end date, only cleanup date.
   *
   * @param processInstanceKey the process instance key
   * @return list of history cleanup dates for all variables
   */
  public List<OffsetDateTime> getVariableCleanupDates(Long processInstanceKey) {
    return queryCleanupDates("VARIABLE", "PROCESS_INSTANCE_KEY", processInstanceKey);
  }

}