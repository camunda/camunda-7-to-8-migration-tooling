/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static io.camunda.migration.data.MigratorMode.LIST_MAPPINGS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class RuntimeMigrationListMappingsTest extends RuntimeMigrationAbstractTest {

  @Autowired
  protected RuntimeService runtimeService;

  @Test
  public void shouldListMigratedProcessInstances(CapturedOutput output) {
    // given: deploy process and migrate instances
    deployer.deployProcessInC7AndC8("simple-process.bpmn");
    
    ProcessInstance pi1 = runtimeService.startProcessInstanceByKey("simpleProcess");
    ProcessInstance pi2 = runtimeService.startProcessInstanceByKey("simpleProcess");
    ProcessInstance pi3 = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Migrate process instances
    runtimeMigrator.start();
    
    // Verify migrations were successful
    assertThatProcessInstanceCountIsEqualTo(3);

    // when: list migration mappings
    runtimeMigrator.setMode(LIST_MAPPINGS);
    runtimeMigrator.start();

    // then: verify output contains migration mappings
    String outputStr = output.getOut();
    
    // Verify header is present
    assertThat(outputStr).contains("Migration mappings for [Process Instances]:");
    
    // Extract all mappings from output (format: "C7_ID -> C8_KEY")
    Pattern mappingPattern = Pattern.compile("([a-f0-9-]+)\\s*->\\s*(\\d+)");
    Matcher matcher = mappingPattern.matcher(outputStr);
    
    Map<String, Long> mappings = new java.util.HashMap<>();
    while (matcher.find()) {
      mappings.put(matcher.group(1), Long.parseLong(matcher.group(2)));
    }
    
    // Verify we have mappings for all three process instances
    assertThat(mappings).hasSize(3);
    assertThat(mappings.keySet()).containsExactlyInAnyOrder(
        pi1.getId(), pi2.getId(), pi3.getId()
    );
    
    // Verify all C8 keys are valid (positive numbers)
    assertThat(mappings.values()).allMatch(key -> key > 0);
  }

  @Test
  public void shouldShowNoMappingsWhenNoInstancesMigrated(CapturedOutput output) {
    // given: deploy process but don't migrate
    deployer.deployProcessInC7AndC8("simple-process.bpmn");
    
    // when: list migration mappings without migrating
    runtimeMigrator.setMode(LIST_MAPPINGS);
    runtimeMigrator.start();

    // then: verify output shows no migrated instances
    String outputStr = output.getOut();
    assertThat(outputStr).contains("No entities of type [Process Instance] were migrated");
  }
}
