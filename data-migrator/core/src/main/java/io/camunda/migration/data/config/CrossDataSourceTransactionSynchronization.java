/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Custom transaction synchronization that coordinates commits and rollbacks across
 * the C8 datasource when the main transaction (on migratorDataSource) completes.
 * 
 * This allows transactions to span two separate datasources without requiring full
 * JTA/XA distributed transaction support.
 * 
 * Limitations:
 * - Does not provide true 2PC guarantees
 * - If the C8 commit succeeds but migrator commit fails, C8 changes remain committed
 * - If the application crashes between commits, partial state may occur
 * - Not suitable for highly critical transactional requirements
 */
public class CrossDataSourceTransactionSynchronization implements TransactionSynchronization {

  private static final Logger LOG = LoggerFactory.getLogger(CrossDataSourceTransactionSynchronization.class);

  private final DataSource c8DataSource;
  private Connection c8Connection;

  public CrossDataSourceTransactionSynchronization(DataSource c8DataSource) {
    this.c8DataSource = c8DataSource;
  }

  /**
   * Gets or creates a connection to the C8 datasource that will be managed by this synchronization.
   */
  public Connection getC8Connection() throws SQLException {
    if (c8Connection == null || c8Connection.isClosed()) {
      c8Connection = c8DataSource.getConnection();
      c8Connection.setAutoCommit(false);
      LOG.debug("Created C8 connection for cross-datasource transaction coordination");
    }
    return c8Connection;
  }

  @Override
  public void suspend() {
    // Called when transaction is suspended (e.g., for propagation)
    LOG.debug("Transaction suspended - C8 connection remains open");
  }

  @Override
  public void resume() {
    // Called when transaction is resumed
    LOG.debug("Transaction resumed");
  }

  @Override
  public void beforeCommit(boolean readOnly) {
    // Called before the main transaction commits
    if (!readOnly && c8Connection != null) {
      try {
        LOG.debug("Committing C8 connection before main transaction commit");
        c8Connection.commit();
      } catch (SQLException e) {
        LOG.error("Failed to commit C8 connection", e);
        throw new RuntimeException("Failed to commit C8 datasource transaction", e);
      }
    }
  }

  @Override
  public void beforeCompletion() {
    // Called before transaction completion (commit or rollback)
    LOG.debug("Before transaction completion");
  }

  @Override
  public void afterCommit() {
    // Called after main transaction has committed
    LOG.debug("Main transaction committed, C8 transaction already committed");
  }

  @Override
  public void afterCompletion(int status) {
    // Called after transaction completion regardless of outcome
    try {
      if (c8Connection != null && !c8Connection.isClosed()) {
        if (status == STATUS_ROLLED_BACK) {
          LOG.debug("Rolling back C8 connection due to main transaction rollback");
          try {
            c8Connection.rollback();
          } catch (SQLException e) {
            LOG.error("Failed to rollback C8 connection", e);
          }
        }
        
        try {
          c8Connection.close();
          LOG.debug("Closed C8 connection");
        } catch (SQLException e) {
          LOG.warn("Failed to close C8 connection", e);
        }
        c8Connection = null;
      }
    } catch (SQLException e) {
      LOG.warn("Error during C8 connection cleanup", e);
    }
  }
}
