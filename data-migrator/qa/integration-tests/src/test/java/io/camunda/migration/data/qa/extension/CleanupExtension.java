/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.extension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.extension.Extension;

/**
 * JUnit extension for querying history cleanup dates from C8 database tables.
 *
 * <p>This extension provides helper methods to verify REMOVAL_TIME column values
 * (history cleanup date) in C8 tables during whitebox testing. Since cleanup dates are not
 * exposed via public API, direct SQL queries are needed to verify correct cleanup date
 * calculation (endDate + TTL).</p>
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
   * @param keyValue the key value to search for
   * @return the history cleanup date, or null if not set
   */
  public OffsetDateTime queryCleanupDate(String tableName, String keyColumn, Long keyValue) {
    return rdbmsQuery.queryForObject(getSql(tableName, keyColumn), CleanupExtension::mapRow, keyValue);
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



}