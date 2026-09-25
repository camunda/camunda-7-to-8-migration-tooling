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
  void replaceProcessInstanceQueryMethods() {
    rewriteRun(spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.List;

@Component
public class HandleProcessInstanceQueryMethodsTestClass {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private CamundaClient camundaClient;

    public void processInstanceQueryMethods(String activityIdIn, String businessKey, String processDefinitionKey) {

        engine.getRuntimeService().createProcessInstanceQuery()
                .activityIdIn(activityIdIn)
                .active()
                .list();

        engine.getRuntimeService().createProcessInstanceQuery()
               .processInstanceBusinessKey(businessKey)
               .active()
               .list();

        engine.getRuntimeService().createProcessInstanceQuery()
               .processInstanceBusinessKey(businessKey)
               .processDefinitionKey(processDefinitionKey);

        engine.getRuntimeService().createProcessInstanceQuery()
               .processDefinitionKey(processDefinitionKey)
               .list();
    }
}
""",
"""
package org.camunda.community.migration.example;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.List;

@Component
public class HandleProcessInstanceQueryMethodsTestClass {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private CamundaClient camundaClient;

    public void processInstanceQueryMethods(String activityIdIn, String businessKey, String processDefinitionKey) {

        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .elementId(activityIdIn)
                        .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items();

        // TODO: processInstanceBusinessKey was removed - use businessId (Camunda 8.9+) instead
        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items();

        // TODO: processInstanceBusinessKey was removed - use businessId (Camunda 8.9+) instead
        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter.processDefinitionId(processDefinitionKey));

        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .processDefinitionId(processDefinitionKey)
                        .state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
                .send()
                .join()
                .items();
    }
}
"""
        ));
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
  void preservesRuntimeStateSemanticsForDefaultAndActiveQueries() {
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
                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(processDefinitionKey)
                                    .state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
                            .send()
                            .join()
                            .items();

                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(processDefinitionKey)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();

                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();

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
                            .list();

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
                                    .state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
                            .send()
                            .join()
                            .items();

                    engine.getTaskService().createTaskQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list();
                }
            }
            """));
  }

  @Test
  void warnsWhenBusinessKeyIsCombinedWithProcessDefinitionKey() {
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
                            .list();
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

            import java.util.List;

            @Component
            public class CombinedQueryTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void combinedQuery(String businessKey, String processDefinitionKey) {
                    // TODO: processInstanceBusinessKey was removed - use businessId (Camunda 8.9+) instead
                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(processDefinitionKey)
                                    .state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
                            .send()
                            .join()
                            .items();
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
                            .list()
                            .size();
                    long activeCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .count();
                    long streamCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .list()
                            .stream()
                            .count();
                    long directCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityId)
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
                            .filter(filter -> filter.state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
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
                                    .state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();
                    long directCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .elementId(activityId)
                                    .state(state -> state.in(ProcessInstanceState.ACTIVE, ProcessInstanceState.SUSPENDED)))
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
}
