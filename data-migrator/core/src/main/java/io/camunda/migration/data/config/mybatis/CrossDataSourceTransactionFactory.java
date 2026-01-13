/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.mybatis;

import io.camunda.migration.data.config.CrossDataSourceTransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom MyBatis TransactionFactory that creates transactions using the
 * cross-datasource transaction manager to coordinate with the main transaction.
 */
public class CrossDataSourceTransactionFactory implements TransactionFactory {

  private static final Logger LOG = LoggerFactory.getLogger(CrossDataSourceTransactionFactory.class);

  private CrossDataSourceTransactionManager transactionManager;

  public void setTransactionManager(CrossDataSourceTransactionManager transactionManager) {
    this.transactionManager = transactionManager;
  }

  @Override
  public Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit) {
    if (transactionManager == null) {
      LOG.warn("CrossDataSourceTransactionManager not set, falling back to default behavior");
      return new ManagedTransaction(dataSource);
    }
    return new CrossDataSourceTransaction(dataSource, transactionManager);
  }

  @Override
  public Transaction newTransaction(Connection conn) {
    // This method is called when MyBatis is given an existing connection
    return new ManagedTransaction(conn);
  }

  /**
   * Simple transaction wrapper that manages an existing connection.
   */
  private static class ManagedTransaction implements Transaction {
    private final Connection connection;
    private final DataSource dataSource;

    public ManagedTransaction(Connection connection) {
      this.connection = connection;
      this.dataSource = null;
    }

    public ManagedTransaction(DataSource dataSource) {
      this.connection = null;
      this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
      if (connection != null) {
        return connection;
      }
      return dataSource.getConnection();
    }

    @Override
    public void commit() throws SQLException {
      // Managed externally
    }

    @Override
    public void rollback() throws SQLException {
      // Managed externally
    }

    @Override
    public void close() throws SQLException {
      // Managed externally
    }

    @Override
    public Integer getTimeout() throws SQLException {
      return null;
    }
  }

  /**
   * Transaction implementation that uses the cross-datasource transaction manager
   * to get connections that are coordinated with the main transaction.
   */
  private static class CrossDataSourceTransaction implements Transaction {
    private static final Logger LOG = LoggerFactory.getLogger(CrossDataSourceTransaction.class);

    private final DataSource dataSource;
    private final CrossDataSourceTransactionManager transactionManager;
    private Connection connection;

    public CrossDataSourceTransaction(DataSource dataSource, CrossDataSourceTransactionManager transactionManager) {
      this.dataSource = dataSource;
      this.transactionManager = transactionManager;
    }

    @Override
    public Connection getConnection() throws SQLException {
      if (connection == null || connection.isClosed()) {
        connection = transactionManager.getC8Connection();
        LOG.debug("Obtained C8 connection from cross-datasource transaction manager");
      }
      return connection;
    }

    @Override
    public void commit() throws SQLException {
      // Commit is handled by the TransactionSynchronization
      LOG.debug("Commit called on CrossDataSourceTransaction - delegating to synchronization");
    }

    @Override
    public void rollback() throws SQLException {
      // Rollback is handled by the TransactionSynchronization
      LOG.debug("Rollback called on CrossDataSourceTransaction - delegating to synchronization");
    }

    @Override
    public void close() throws SQLException {
      // Connection cleanup is handled by the TransactionSynchronization
      LOG.debug("Close called on CrossDataSourceTransaction - connection managed by synchronization");
    }

    @Override
    public Integer getTimeout() throws SQLException {
      return null;
    }
  }
}
