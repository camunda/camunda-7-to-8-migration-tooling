/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.identity;

import io.camunda.client.CamundaClient;
import io.camunda.migrator.IdentityMigrator;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.qa.AbstractMigratorTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.camunda.bpm.engine.IdentityService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public class IdentityAbstractTest extends AbstractMigratorTest {

  @Autowired
  protected CamundaClient camundaClient;

  @Autowired
  protected IdentityService identityService;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected IdentityMigrator identityMigrator;

  @AfterEach
  public void cleanup() {
    identityService.createTenantQuery().list().forEach(tenant -> identityService.deleteTenant(tenant.getId()));
    dbClient.deleteAllMappings();
  }
}
