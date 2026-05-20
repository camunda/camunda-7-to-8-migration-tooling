/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.Test;

class HistoryClientApiUsageTest {

  @Test
  void shouldNotDependOnDbReaderServicesInHistoryHelpers() {
    var importedClasses = new ClassFileImporter()
        .importClasses(HistoryMigrationAbstractTest.class, HistoryMigrationExtension.class);

    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.camunda.db.rdbms.read.service..")
        .check(importedClasses);
  }
}
