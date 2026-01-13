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
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Manages cross-datasource transaction coordination by registering a
 * TransactionSynchronization that handles C8 datasource commits/rollbacks
 * in sync with the main transaction.
 */
@Component
public class CrossDataSourceTransactionManager {

  private static final Logger LOG = LoggerFactory.getLogger(CrossDataSourceTransactionManager.class);
  private static final String SYNC_KEY = CrossDataSourceTransactionSynchronization.class.getName();

  private final DataSource c8DataSource;

  public CrossDataSourceTransactionManager(DataSource c8DataSource) {
    this.c8DataSource = c8DataSource;
  }

  /**
   * Gets the C8 connection for the current transaction, registering the
   * synchronization if this is the first call in the transaction.
   */
  public Connection getC8Connection() throws SQLException {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // No active transaction - return a connection with auto-commit enabled
      LOG.warn("No active transaction - C8 connection will use auto-commit");
      return c8DataSource.getConnection();
    }

    CrossDataSourceTransactionSynchronization sync = 
        (CrossDataSourceTransactionSynchronization) TransactionSynchronizationManager.getResource(SYNC_KEY);

    if (sync == null) {
      // First C8 operation in this transaction - register synchronization
      sync = new CrossDataSourceTransactionSynchronization(c8DataSource);
      TransactionSynchronizationManager.registerSynchronization(sync);
      TransactionSynchronizationManager.bindResource(SYNC_KEY, sync);
      LOG.debug("Registered cross-datasource transaction synchronization for C8");
    }

    return sync.getC8Connection();
  }

  /**
   * Checks if there's an active transaction with synchronization support.
   */
  public boolean isTransactionActive() {
    return TransactionSynchronizationManager.isSynchronizationActive();
  }
}
