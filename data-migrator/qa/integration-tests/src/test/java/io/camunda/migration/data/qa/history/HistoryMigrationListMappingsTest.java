/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.MigratorMode.LIST_MAPPINGS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class HistoryMigrationListMappingsTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected HistoryService historyService;

  @Test
  public void shouldListMigratedHistoryEntities(CapturedOutput output) {
    // given: deploy and run processes
    deployer.deployProcessInC7AndC8("simple-process.bpmn");
    deployer.deployDecisionInC7AndC8("simple-decision.dmn");
    
    // Start and complete process instances to generate history
    for (int i = 0; i < 3; i++) {
      var pi = runtimeService.startProcessInstanceByKey("simpleProcess");
      runtimeService.deleteProcessInstance(pi.getId(), "test cleanup");
    }

    // Migrate history
    historyMigrator.migrate();

    // when: list migration mappings
    historyMigrator.setMode(LIST_MAPPINGS);
    historyMigrator.start();

    // then: verify output contains migration mappings for various entity types
    String outputStr = output.getOut();
    
    // Verify headers for different entity types are present
    assertThat(outputStr).contains("Migration mappings for [Historic Process Definitions]:");
    assertThat(outputStr).contains("Migration mappings for [Historic Process Instances]:");
    assertThat(outputStr).contains("Migration mappings for [Historic Decision Definitions]:");
    
    // Extract all mappings from output (format: "C7_ID -> C8_KEY")
    Map<String, List<String>> mappingsBySection = parseMappingsBySections(outputStr);
    
    // Verify we have mappings for process instances
    assertThat(mappingsBySection).containsKey("Historic Process Instances");
    List<String> processInstanceMappings = mappingsBySection.get("Historic Process Instances");
    assertThat(processInstanceMappings).isNotEmpty();
    
    // Verify all process instances from C7 are in the mappings
    List<HistoricProcessInstance> historicInstances = historyService
        .createHistoricProcessInstanceQuery()
        .list();
    assertThat(processInstanceMappings.size()).isGreaterThanOrEqualTo(historicInstances.size());
  }

  @Test
  public void shouldListMigratedHistoryEntitiesWithFilter(CapturedOutput output) {
    // given: deploy and run processes
    deployer.deployProcessInC7AndC8("simple-process.bpmn");
    
    // Start and complete a process instance
    var pi = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.deleteProcessInstance(pi.getId(), "test cleanup");

    // Migrate history
    historyMigrator.migrate();

    // when: list migration mappings with filter
    historyMigrator.setMode(LIST_MAPPINGS);
    historyMigrator.setRequestedEntityTypes(List.of(
        IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE
    ));
    historyMigrator.start();

    // then: verify output contains only filtered entity type
    String outputStr = output.getOut();
    
    assertThat(outputStr).contains("Migration mappings for [Historic Process Instances]:");
    // Should not contain other entity types when filtered
    assertThat(outputStr).doesNotContain("Migration mappings for [Historic Process Definitions]:");
  }

  @Test
  public void shouldShowNoMappingsWhenNoHistoryMigrated(CapturedOutput output) {
    // given: deploy process but don't migrate history
    deployer.deployProcessInC7AndC8("simple-process.bpmn");
    
    // when: list migration mappings without migrating
    historyMigrator.setMode(LIST_MAPPINGS);
    historyMigrator.start();

    // then: verify output shows no migrated instances
    String outputStr = output.getOut();
    assertThat(outputStr).contains("No entities of type [Historic Process Definition] were migrated");
    assertThat(outputStr).contains("No entities of type [Historic Process Instance] were migrated");
  }

  /**
   * Parse mappings grouped by their section headers.
   */
  protected Map<String, List<String>> parseMappingsBySections(String output) {
    Map<String, List<String>> result = new HashMap<>();
    
    // Pattern to match section headers like "Migration mappings for [Entity Type]:"
    Pattern headerPattern = Pattern.compile("Migration mappings for \\[([^\\]]+)\\]:");
    // Pattern to match individual mappings "ID -> KEY"
    Pattern mappingPattern = Pattern.compile("([a-f0-9-]+)\\s*->\\s*(\\d+)");
    
    String[] lines = output.split("\n");
    String currentSection = null;
    
    for (String line : lines) {
      Matcher headerMatcher = headerPattern.matcher(line);
      if (headerMatcher.find()) {
        currentSection = headerMatcher.group(1);
        result.put(currentSection, new ArrayList<>());
      } else if (currentSection != null) {
        Matcher mappingMatcher = mappingPattern.matcher(line);
        if (mappingMatcher.find()) {
          result.get(currentSection).add(mappingMatcher.group(1) + " -> " + mappingMatcher.group(2));
        }
      }
    }
    
    return result;
  }
}
