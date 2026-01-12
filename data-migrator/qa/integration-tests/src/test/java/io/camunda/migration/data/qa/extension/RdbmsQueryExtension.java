/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.extension;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Generic JUnit 5 extension for executing SQL queries against RDBMS databases in tests.
 * Provides convenient methods for querying database tables and verifying data.
 *
 * <p>Usage in tests:</p>
 * <pre>
 * {@literal @}RegisterExtension
 * {@literal @}Autowired
 * RdbmsQueryExtension rdbmsQuery;
 *
 * // Query single row
 * Map&lt;String, Object&gt; result = rdbmsQuery.queryForMap(
 *     "SELECT * FROM MY_TABLE WHERE KEY = ?", key);
 *
 * // Query multiple rows
 * List&lt;Map&lt;String, Object&gt;&gt; results = rdbmsQuery.queryForList(
 *     "SELECT * FROM MY_TABLE WHERE PARENT_KEY = ?", parentKey);
 *
 * // Query with custom mapper
 * MyEntity entity = rdbmsQuery.queryForObject(
 *     "SELECT * FROM MY_TABLE WHERE KEY = ?",
 *     (rs, rowNum) -&gt; new MyEntity(...),
 *     key);
 * </pre>
 */
@Component
public class RdbmsQueryExtension implements BeforeEachCallback, AfterEachCallback, ApplicationContextAware {

  private static ApplicationContext applicationContext;
  private JdbcTemplate jdbcTemplate;
  private final String dataSourceBeanName;

  /**
   * Creates extension using the default C8 data source.
   */
  public RdbmsQueryExtension() {
    this("c8DataSource");
  }

  /**
   * Creates extension using a specific data source bean name.
   *
   * @param dataSourceBeanName the name of the DataSource bean to use
   */
  public RdbmsQueryExtension(String dataSourceBeanName) {
    this.dataSourceBeanName = dataSourceBeanName;
  }

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    RdbmsQueryExtension.applicationContext = context;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    if (applicationContext == null) {
      applicationContext = SpringExtension.getApplicationContext(context);
    }

    if (jdbcTemplate == null && applicationContext != null) {
      DataSource dataSource = applicationContext.getBean(dataSourceBeanName, DataSource.class);
      jdbcTemplate = new JdbcTemplate(dataSource);
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // Cleanup if needed - currently no cleanup required
  }

  /**
   * Gets the underlying JdbcTemplate for advanced usage.
   *
   * @return the JdbcTemplate instance
   */
  public JdbcTemplate getJdbcTemplate() {
    if (jdbcTemplate == null) {
      throw new IllegalStateException("JdbcTemplate not initialized. Make sure the extension is properly registered.");
    }
    return jdbcTemplate;
  }

  /**
   * Query for a single row, returning the result as a Map.
   *
   * @param sql the SQL query
   * @param args the query parameters
   * @return a map with column names as keys
   */
  public Map<String, Object> queryForMap(String sql, Object... args) {
    return getJdbcTemplate().queryForMap(sql, args);
  }

  /**
   * Query for multiple rows, returning each row as a Map.
   *
   * @param sql the SQL query
   * @param args the query parameters
   * @return list of maps with column names as keys
   */
  public List<Map<String, Object>> queryForList(String sql, Object... args) {
    return getJdbcTemplate().queryForList(sql, args);
  }

  /**
   * Query for a single object using a custom RowMapper.
   *
   * @param <T> the result type
   * @param sql the SQL query
   * @param rowMapper the mapper to convert ResultSet to object
   * @param args the query parameters
   * @return the mapped object
   */
  public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
    return getJdbcTemplate().queryForObject(sql, rowMapper, args);
  }

  /**
   * Query for multiple objects using a custom RowMapper.
   *
   * @param <T> the result type
   * @param sql the SQL query
   * @param rowMapper the mapper to convert ResultSet to objects
   * @param args the query parameters
   * @return list of mapped objects
   */
  public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
    return getJdbcTemplate().query(sql, rowMapper, args);
  }

  /**
   * Query for a single value.
   *
   * @param <T> the result type
   * @param sql the SQL query
   * @param requiredType the required type of the result
   * @param args the query parameters
   * @return the single value
   */
  public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
    return getJdbcTemplate().queryForObject(sql, requiredType, args);
  }

  /**
   * Execute an update/insert/delete statement.
   *
   * @param sql the SQL statement
   * @param args the statement parameters
   * @return the number of rows affected
   */
  public int update(String sql, Object... args) {
    return getJdbcTemplate().update(sql, args);
  }

  /**
   * Execute a SQL statement (for DDL or statements without parameters).
   *
   * @param sql the SQL statement
   */
  public void execute(String sql) {
    getJdbcTemplate().execute(sql);
  }

  /**
   * Check if a record exists in a table.
   *
   * @param tableName the table name
   * @param keyColumn the key column name
   * @param keyValue the key value
   * @return true if record exists
   */
  public boolean exists(String tableName, String keyColumn, Object keyValue) {
    String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", tableName, keyColumn);
    Integer count = queryForObject(sql, Integer.class, keyValue);
    return count != null && count > 0;
  }

  /**
   * Count rows in a table matching a condition.
   *
   * @param tableName the table name
   * @param whereClause the WHERE clause (without "WHERE" keyword)
   * @param args the query parameters
   * @return the row count
   */
  public int count(String tableName, String whereClause, Object... args) {
    String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereClause);
    Integer count = queryForObject(sql, Integer.class, args);
    return count != null ? count : 0;
  }

}