/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.MigrateProcessInstanceQueryMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

public class ReplaceProcessInstanceQueryMethodsTest implements RewriteTest {

  @Test
  void leavesActiveProcessInstanceListsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceQueryMethods {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String activityId, String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .activityIdIn(activityId)
                            .active()
                            .list();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesUnboundedProcessInstanceListsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnboundedProcessInstanceLists {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public List<ProcessInstance> search(String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();

                    List<ProcessInstance> declared = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();

                    List<ProcessInstance> assigned;
                    assigned = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();

                    return engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesVariableFilteredQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceVariableQueries {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String variableName, Object variableValue,
                        String secondVariableName, Object secondVariableValue) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueEquals(variableName, variableValue)
                            .active()
                            .list();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueEquals(variableName, variableValue)
                            .count();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueEquals(variableName, variableValue)
                            .singleResult();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueEquals(variableName, variableValue)
                            .variableValueEquals(secondVariableName, secondVariableValue)
                            .active()
                            .list();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueEquals(variableName, variableValue)
                            .variableValueEquals(secondVariableName, secondVariableValue)
                            .singleResult();
                }
            }
            """));
  }

  @Test
  void leavesAllVariableFilterMethodsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceVariablePredicates {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String variableName, Object variableValue,
                        String variablePattern) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueNotEquals(variableName, variableValue)
                            .active()
                            .list();
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueGreaterThan(variableName, variableValue)
                            .active()
                            .list();
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueGreaterThanOrEqual(variableName, variableValue)
                            .active()
                            .list();
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueLessThan(variableName, variableValue)
                            .active()
                            .list();
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueLessThanOrEqual(variableName, variableValue)
                            .active()
                            .list();
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueLike(variableName, variablePattern)
                            .active()
                            .list();

                    ProcessInstanceQuery query = engine.getRuntimeService().createProcessInstanceQuery()
                            .variableValueLike(variableName, variablePattern);
                    query.processDefinitionKey(processDefinitionKey).active().list();
                }
            }
            """));
  }

  @Test
  void leavesVariableFilteredQueryAliasesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceVariableQueryAlias {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String variableName, Object variableValue) {
                    ProcessInstanceQuery query = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .variableValueEquals(variableName, variableValue);
                    query.processDefinitionKey(processDefinitionKey).list();
                    query.processDefinitionKey(processDefinitionKey).count();
                    query.processDefinitionKey(processDefinitionKey).list().size();
                }
            }
            """));
  }

  @Test
  void leavesVariableFilteredQueryFieldsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceVariableQueryField {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                private ProcessInstanceQuery query;

                public void setQuery(String variableName, Object variableValue) {
                    this.query = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .variableValueEquals(variableName, variableValue);
                }

                public void search(String processDefinitionKey) {
                    this.query.processDefinitionKey(processDefinitionKey).list();
                    this.query.processDefinitionKey(processDefinitionKey).count();
                    this.query.processDefinitionKey(processDefinitionKey).list().size();
                }
            }
            """));
  }

  @Test
  void leavesHelperReturnedVariableFilteredQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceVariableQueryHelper {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                private ProcessInstanceQuery query(String variableName, Object variableValue) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .variableValueEquals(variableName, variableValue);
                }

                public void search(String processDefinitionKey, String variableName, Object variableValue) {
                    query(variableName, variableValue).processDefinitionKey(processDefinitionKey).list();
                    query(variableName, variableValue).processDefinitionKey(processDefinitionKey).count();
                }
            }
            """));
  }

  @Test
  void leavesUntraceableProcessInstanceQueryParametersForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UntraceableProcessInstanceQueryParameter {

                @Autowired
                private CamundaClient camundaClient;

                public void search(ProcessInstanceQuery query, String processDefinitionKey) {
                    query.active().processDefinitionKey(processDefinitionKey).list();
                    query.active().processDefinitionKey(processDefinitionKey).count();
                }
            }
            """));
  }

  @Test
  void leavesQueryAliasesWithFiltersAppliedInSeparateStatementsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceQueryWithSeparateFilters {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String businessKey, String activityId,
                        String processInstanceId) {
                    ProcessInstanceQuery query = engine.getRuntimeService().createProcessInstanceQuery();
                    query.processDefinitionKey(processDefinitionKey);
                    query.processInstanceBusinessKey(businessKey);
                    query.activityIdIn(activityId);
                    query.processInstanceId(processInstanceId);
                    query.active().list();
                    query.active().count();
                }
            }
            """));
  }

  @Test
  void leavesQueryAliasesWithPreAppliedActiveStateForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceQueryWithPreAppliedActiveState {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    ProcessInstanceQuery query = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active();
                    List<ProcessInstance> result =
                            query.processDefinitionKey(processDefinitionKey).list();

                    List<ProcessInstance> assignmentTarget = null;
                    assignmentTarget =
                            query.processDefinitionKey(processDefinitionKey).list();
                }
            }
            """));
  }

  @Test
  void leavesUntraceableQueryAliasListResultTypesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UntraceableProcessInstanceQueryResult {

                @Autowired
                private CamundaClient camundaClient;

                public void search(ProcessInstanceQuery query, String processDefinitionKey) {
                    List<ProcessInstance> result =
                            query.active().processDefinitionKey(processDefinitionKey).list();

                    List<ProcessInstance> assignmentTarget = null;
                    assignmentTarget =
                            query.active().processDefinitionKey(processDefinitionKey).list();
                    ProcessInstance instanceFromAssignment = assignmentTarget.get(0);

                    List<ProcessInstance> parenthesizedAssignmentTarget = null;
                    parenthesizedAssignmentTarget =
                            (query.active().processDefinitionKey(processDefinitionKey).list());
                    ProcessInstance instanceFromParenthesizedAssignment =
                            parenthesizedAssignmentTarget.get(0);
                }
            }
            """));
  }

  @Test
  void leavesProcessInstanceListFieldAssignmentsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceListFieldAssignment {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                private List<ProcessInstance> instances;

                public void search(String processDefinitionKey) {
                    this.instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();

                    instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesParenthesizedProcessInstanceListDeclarationsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceList {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    List<ProcessInstance> result =
                            (engine.getRuntimeService()
                                    .createProcessInstanceQuery()
                                    .processDefinitionKey(processDefinitionKey)
                                    .active()
                                    .list());
                }
            }
            """));
  }

  @Test
  void leavesParenthesizedProcessInstanceListAssignmentsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceListAssignment {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    List<ProcessInstance> result = null;
                    result = (
                            engine.getRuntimeService()
                                    .createProcessInstanceQuery()
                                    .active()
                                    .processDefinitionKey(processDefinitionKey)
                                    .list());
                }
            }
            """));
  }

  @Test
  void leavesIncompleteInlineActiveProcessInstanceQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class IncompleteActiveProcessInstanceQueries {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .processDefinitionKey(processDefinitionKey);

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active();
                }
            }
            """));
  }

  @Test
  void leavesFieldsAssignedFromHelperReturnedVariableFilteredQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class HelperReturnedProcessInstanceQueryField {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                private ProcessInstanceQuery query;

                private ProcessInstanceQuery filteredQuery(String variableName, Object variableValue) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .variableValueEquals(variableName, variableValue);
                }

                public void search(String processDefinitionKey, String variableName, Object variableValue) {
                    this.query = filteredQuery(variableName, variableValue);
                    this.query.active().processDefinitionKey(processDefinitionKey).list();
                    this.query.active().processDefinitionKey(processDefinitionKey).count();
                }
            }
            """));
  }

  @Test
  void leavesTraceableProcessInstanceQueryAliasesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class TraceableProcessInstanceQueryAlias {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    ProcessInstanceQuery query = engine.getRuntimeService().createProcessInstanceQuery();
                    query.active().processDefinitionKey(processDefinitionKey).list();
                }
            }
            """));
  }

  @Test
  void leavesDefaultStateAndUnboundedListQueriesManualAndConvertsCounts() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceActiveFilter {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .list()
                            .size();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .count();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .active()
                            .count();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceActiveFilter {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .list()
                            .size();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .count();

                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();
                }
            }
            """));
  }

  @Test
  void leavesSuspendedQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class SuspendedProcessInstanceQueries {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .suspended()
                            .list();

                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .suspended()
                            .count();
                }
            }
            """));
  }

  @Test
  void leavesTaskSingleResultUnchangedWhenProcessInstanceQueriesArePresent() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.task.Task;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class MixedSingleResultQueries {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void find(String taskId, String processDefinitionKey, String variableName, Object variableValue) {
                    Task task = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .variableValueEquals(variableName, variableValue)
                            .singleResult();
                }
            }
            """));
  }

  @Test
  void doesNotRewriteTaskQueryListChains() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;
            import io.camunda.client.CamundaClient;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            import java.util.List;

            @Component
            public class MixedQueryTypesTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void mixedQueries(String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .count();

                    engine.getTaskService().createTaskQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;
            import io.camunda.client.CamundaClient;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            import java.util.List;

            @Component
            public class MixedQueryTypesTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void mixedQueries(String processDefinitionKey) {
                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(processDefinitionKey)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();

                    engine.getTaskService().createTaskQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesProcessInstanceListStreamOperationsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceListStreams {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search() {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .stream()
                            .filter(instance -> instance.getId() != null)
                            .count();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .parallelStream()
                            .count();

                    java.util.List<ProcessInstance> instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list();
                    instances.stream()
                            .filter(instance -> instance.getId() != null)
                            .count();

                    java.util.List<ProcessInstance> assignedInstances;
                    assignedInstances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list();
                    assignedInstances.parallelStream().count();
                }
            }
            """));
  }

  @Test
  void leavesActiveBusinessKeyQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceBusinessKeyQueries {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String businessKey) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .active()
                            .list();
                }

                public long count(String businessKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .active()
                            .count();
                }

                public long countForDefinition(String businessKey, String processDefinitionKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .count();
                }
            }
            """));
  }

  @Test
  void leavesBusinessKeyQueriesCombinedWithProcessDefinitionKeyForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            import java.util.List;

            @Component
            public class CombinedQueryTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void combinedQuery(String businessKey, String processDefinitionKey) {
                    engine.getRuntimeService().createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list();
                }
            }
            """));
  }

  @Test
  void replacesCompleteProcessInstanceCounts() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceCounts {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void count(String processDefinitionKey, String activityId) {
                    int sizeCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                    long activeCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .count();
                    long streamCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .active()
                            .list()
                            .stream()
                            .count();
                    long directCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityId)
                            .active()
                            .count();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceCounts {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void count(String processDefinitionKey, String activityId) {
                    int sizeCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                    long activeCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();
                    long streamCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(processDefinitionKey)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();
                    long directCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .elementId(activityId)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();
                }
            }
            """));
  }

  @Test
  void doesNotPartiallyRewriteUnsupportedCountQueries() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnsupportedProcessInstanceCount {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public long count(String processDefinitionKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .suspended()
                            .count();
                }
            }
            """));
  }

  @Test
  void leavesListQueriesWithUnsupportedFilterCombinationsUnchanged() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnsupportedProcessInstanceListFilters {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String activityId, String otherProcessDefinitionKey) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .activityIdIn(activityId)
                            .active()
                            .list();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .processDefinitionKey(otherProcessDefinitionKey)
                            .active()
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesParenthesizedBusinessKeyQueriesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceQueryFilters {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String businessKey, String processDefinitionKey) {
                    (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey))
                            .active()
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                }
            }
            """));
  }

  @Test
  void convertsCountsForParenthesizedListResults() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceCount {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int count(String processDefinitionKey) {
                    return (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .processDefinitionKey(processDefinitionKey)
                            .list()).size();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceCount {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int count(String processDefinitionKey) {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(processDefinitionKey)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                }
            }
            """));
  }

  @Test
  void leavesActivityIdInWithUnsupportedAritiesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnsupportedActivityIdInArities {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String activityId, String otherActivityId) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .activityIdIn(activityId, otherActivityId)
                            .active()
                            .list();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .activityIdIn(activityId, otherActivityId)
                            .active()
                            .count();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn()
                            .active()
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesQueriesWithUnsupportedNonVariableFiltersForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnsupportedProcessInstanceFilters {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey, String processInstanceId) {
                    List<ProcessInstance> declared = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .processInstanceId(processInstanceId)
                            .active()
                            .list();

                    List<ProcessInstance> assigned;
                    assigned = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .processInstanceId(processInstanceId)
                            .active()
                            .list();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .processInstanceId(processInstanceId)
                            .active()
                            .count();
                }
            }
            """));
  }

  @Test
  void leavesUnsupportedQueryTerminalsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnsupportedProcessInstanceTerminals {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    ProcessInstance single = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .singleResult();
                    List<ProcessInstance> page = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .listPage(0, 10);
                    List<ProcessInstance> all = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .unlimitedList();
                }
            }
            """));
  }

  @Test
  void preservesDefaultStateQueryTypesForDeclarationsAndAssignments() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class DefaultStateProcessInstanceResultTypes {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    List<ProcessInstance> declared = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                    List<ProcessInstance> assigned;
                    assigned = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                }
            }
            """));
  }

  @Test
  void leavesProcessInstanceQueryAliasesForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceQueryAliases {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    ProcessInstanceQuery declared = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey);
                    declared.list();

                    ProcessInstanceQuery assigned;
                    assigned = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey);
                    assigned.list();

                    ProcessInstanceQuery stateChanged = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey);
                    stateChanged = stateChanged.active();
                    stateChanged.list();
                }
            }
            """));
  }

  @Test
  void leavesActiveQueriesWithParenthesizedReceiversForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceQuery {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void search(String processDefinitionKey) {
                    (engine.getRuntimeService().createProcessInstanceQuery().active())
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                }
            }
            """));
  }
}
