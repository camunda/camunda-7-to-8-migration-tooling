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
                        .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items();
    }
}
"""
        ));
  }

  @Test
  void addsStateImportForBusinessKeyListQueries() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;

            public class BusinessKeyListQueryTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public void query(String businessKey) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .active()
                            .list();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;

            public class BusinessKeyListQueryTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public void query(String businessKey) {
                    // TODO: processInstanceBusinessKey was removed - use businessId (Camunda 8.9+) instead
                    camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
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
            import org.camunda.bpm.engine.task.Task;
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

                    int taskCount = engine.getTaskService().createTaskQuery()
                            .list()
                            .size();

                    List<Task> tasks = engine.getTaskService().createTaskQuery()
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
            import org.camunda.bpm.engine.task.Task;
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
                            .items();

                    int taskCount = engine.getTaskService().createTaskQuery()
                            .list()
                            .size();

                    List<Task> tasks = engine.getTaskService().createTaskQuery()
                            .list();

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
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                }
            }
            """));
  }

  @Test
  void replacesUnfilteredProcessInstanceCounts() {
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
            public class UnfilteredProcessInstanceCountsTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void countQueries() {
                    int listSize = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();

                    long streamCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .stream()
                            .count();

                    long directCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
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
            public class UnfilteredProcessInstanceCountsTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void countQueries() {
                    int listSize = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();

                    long streamCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();

                    long directCount = camundaClient
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
  void preservesIntResultForListSizeExpressions() {
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
            public class ProcessInstanceQueryCountExpressionTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countProcessInstances() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                }

                public int countProcessInstancesWithTernary(boolean useCount) {
                    int count = useCount
                            ? engine.getRuntimeService()
                                    .createProcessInstanceQuery()
                                    .active()
                                    .list()
                                    .size()
                            : 0;
                    return count;
                }

                public int countProcessInstancesWithAssignment() {
                    int count = 0;
                    count = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                    return count;
                }

                public int countProcessInstancesWithMultipleVariables() {
                    int other = 0, count = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                    return other + count;
                }

                private void acceptInt(int count) {}

                public void countProcessInstancesAsMethodArgument() {
                    acceptInt(engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size());
                }

                public void countProcessInstancesAsParenthesizedMethodArgument() {
                    acceptInt((engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()));
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
            public class ProcessInstanceQueryCountExpressionTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int countProcessInstances() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int countProcessInstancesWithTernary(boolean useCount) {
                    int count = useCount
                            ? camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()
                            : 0;
                    return count;
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int countProcessInstancesWithAssignment() {
                    int count = 0;
                    count = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                    return count;
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int countProcessInstancesWithMultipleVariables() {
                    int other = 0, count = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                    return other + count;
                }

                private void acceptInt(int count) {}

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void countProcessInstancesAsMethodArgument() {
                    acceptInt(camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue());
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void countProcessInstancesAsParenthesizedMethodArgument() {
                    acceptInt((camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()));
                }
            }
            """));
  }

  @Test
  void preservesIntResultForSwitchAndArrayContexts() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            import java.util.function.IntSupplier;

            @Component
            public class ProcessInstanceQueryIntContextTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int switchOnCount() {
                    switch (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()) {
                        case 0:
                            return 0;
                        default:
                            return 1;
                    }
                }

                public int arrayIndex(int[] values) {
                    return values[engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()];
                }

                public int[] arrayDimension() {
                    return new int[engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()];
                }

                public int[] arrayInitializer() {
                    int[] values = {engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()};
                    return values;
                }

                public IntSupplier intSupplier() {
                    IntSupplier count = () -> engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                    return count;
                }

                private void acceptInt(int count) {}

                private void acceptInts(int... counts) {}

                public void nestedMethodArguments() {
                    acceptInt(engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size() + 1);
                    acceptInts(engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size());
                }

                public int[] nestedArrayDimension() {
                    return new int[engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size() + 1];
                }

                public int nestedSwitchSelector() {
                    switch (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size() + 1) {
                        case 0:
                            return 0;
                        default:
                            return 1;
                    }
                }

                public String nestedSwitchExpression() {
                    return switch (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size() + 1) {
                        case 0 -> "zero";
                        default -> "other";
                    };
                }

                public int[] nestedArrayInitializer() {
                    return new int[] {engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size() + 1};
                }

                public int parenthesizedListSize() {
                    return (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list())
                            .size();
                }

                public long parenthesizedStreamCount() {
                    return (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list())
                            .stream()
                            .count();
                }

                public int parenthesizedReceiverChainListSize() {
                    return (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active())
                            .list()
                            .size();
                }

                public int parenthesizedFilteredListSize() {
                    return (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process"))
                            .list()
                            .size();
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

            import java.util.function.IntSupplier;

            @Component
            public class ProcessInstanceQueryIntContextTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int switchOnCount() {
                    switch (camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()) {
                        case 0:
                            return 0;
                        default:
                            return 1;
                    }
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int arrayIndex(int[] values) {
                    return values[camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()];
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int[] arrayDimension() {
                    return new int[camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()];
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int[] arrayInitializer() {
                    int[] values = {camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()};
                    return values;
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public IntSupplier intSupplier() {
                    IntSupplier count = () -> camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                    return count;
                }

                private void acceptInt(int count) {}

                private void acceptInts(int... counts) {}

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void nestedMethodArguments() {
                    acceptInt(camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue() + 1);
                    acceptInts(camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue());
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int[] nestedArrayDimension() {
                    return new int[camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue() + 1];
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int nestedSwitchSelector() {
                    switch (camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue() + 1) {
                        case 0:
                            return 0;
                        default:
                            return 1;
                    }
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public String nestedSwitchExpression() {
                    return switch (camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue() + 1) {
                        case 0 -> "zero";
                        default -> "other";
                    };
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int[] nestedArrayInitializer() {
                    return new int[] {camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue() + 1};
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int parenthesizedListSize() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long parenthesizedStreamCount() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int parenthesizedReceiverChainListSize() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public int parenthesizedFilteredListSize() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
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
  void preservesIntResultForConstructorAndCompoundAssignment() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.concurrent.atomic.AtomicInteger;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceQueryAssignmentContextTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void countQueries() {
                    int total = 0;
                    total += engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();

                    int assigned = 0;
                    assigned = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();

                    AtomicInteger count = new AtomicInteger(engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size());
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;

            import java.util.concurrent.atomic.AtomicInteger;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ProcessInstanceQueryAssignmentContextTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void countQueries() {
                    int total = 0;
                    total += camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();

                    int assigned = 0;
                    assigned = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();

                    AtomicInteger count = new AtomicInteger(camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue());
                }
            }
            """));
  }

  @Test
  void flagsCountsOnAssignedProcessInstanceLists() {
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
            public class AssignedProcessInstanceListCountTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countInstances() {
                    List<ProcessInstance> instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                    return instances.size();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;

            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class AssignedProcessInstanceListCountTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countInstances() {
                    List<io.camunda.client.api.search.response.ProcessInstance> instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }
            }
            """));
  }

  @Test
  void preservesIntResultForUnknownContexts() {
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
            public class ProcessInstanceQueryUnknownContextTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public Object objectCount() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                }

                public Number numberCount() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                }

                private void acceptObject(Object count) {}

                public void genericArgument() {
                    acceptObject(engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size());
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
            public class ProcessInstanceQueryUnknownContextTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public Object objectCount() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public Number numberCount() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();
                }

                private void acceptObject(Object count) {}

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void genericArgument() {
                    acceptObject(camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue());
                }
            }
            """));
  }

  @Test
  void flagsCountsForAssignedProcessInstanceLists() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class AssignedProcessInstanceListCountTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countInstances() {
                    List<?> instances;
                    instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                    return instances.size();
                }

                public int countUnfilteredInstances() {
                    List<?> instances;
                    instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list();
                    return instances.size();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;

            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class AssignedProcessInstanceListCountTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countInstances() {
                    List<?> instances;
                    instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }

                public int countUnfilteredInstances() {
                    List<?> instances;
                    instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }
            }
            """));
  }

  @Test
  void flagsCountsForParenthesizedQueryResultsAndFieldAssignments() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceListCountTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                private List<?> fieldInstances;

                public int countDeclaredInstances() {
                    List<?> instances = (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list());
                    return instances.size();
                }

                public long countDeclaredStreamInstances() {
                    List<?> instances = (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list());
                    return instances.stream().count();
                }

                public int countNestedAssignedInstances() {
                    List<?> instances;
                    if (true) {
                        instances = (engine.getRuntimeService()
                                .createProcessInstanceQuery()
                                .processDefinitionKey("order-process")
                                .active()
                                .list());
                    }
                    return instances.size();
                }

                public void assignFieldInstances() {
                    fieldInstances = (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list());
                }

                public int countFieldInstances() {
                    return fieldInstances.size();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import io.camunda.client.api.search.response.ProcessInstance;

            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ParenthesizedProcessInstanceListCountTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                private List<?> fieldInstances;

                public int countDeclaredInstances() {
                    List<ProcessInstance> instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }

                public long countDeclaredStreamInstances() {
                    List<ProcessInstance> instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.stream().count();
                }

                public int countNestedAssignedInstances() {
                    List<?> instances;
                    if (true) {
                        instances = camundaClient
                                .newProcessInstanceSearchRequest()
                                .filter(filter -> filter
                                        .processDefinitionId("order-process")
                                        .state(ProcessInstanceState.ACTIVE))
                                .send()
                                .join()
                                .items();
                    }
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }

                public void assignFieldInstances() {
                    fieldInstances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                }

                public int countFieldInstances() {
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: fieldInstances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ fieldInstances.size();
                }
            }
            """));
  }

  @Test
  void preservesLongResultForListSizeExpressions() {
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
            public class ProcessInstanceQueryLongCountExpressionTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public long countProcessInstances() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                }

                public long assignProcessInstances() {
                    long count = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size();
                    return count;
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
            public class ProcessInstanceQueryLongCountExpressionTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long countProcessInstances() {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems();
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long assignProcessInstances() {
                    long count = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems();
                    return count;
                }
            }
            """));
  }

  @Test
  void replacesFilteredProcessInstanceCounts() {
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
            public class FilteredProcessInstanceCountsTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public void countQueries(String activityId, String businessKey) {
                    long activityCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityId)
                            .count();

                    int activityListSize = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityId)
                            .active()
                            .list()
                            .size();

                    long businessCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .count();

                    long combinedCount = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey)
                            .processDefinitionKey("example-process")
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
            public class FilteredProcessInstanceCountsTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public void countQueries(String activityId, String businessKey) {
                    long activityCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .elementId(activityId)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();

                    int activityListSize = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .elementId(activityId)
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue();

                    // TODO: processInstanceBusinessKey was removed - use businessId (Camunda 8.9+) instead
                    long businessCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue();

                    // TODO: processInstanceBusinessKey was removed - use businessId (Camunda 8.9+) instead
                    long combinedCount = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("example-process")
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
  void usesLastRepeatedProcessInstanceFilter() {
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
            public class RepeatedProcessInstanceFilterTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public long countWithRepeatedProcessDefinitionKey(
                        String firstProcessDefinitionKey, String secondProcessDefinitionKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(firstProcessDefinitionKey)
                            .processDefinitionKey(secondProcessDefinitionKey)
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
            public class RepeatedProcessInstanceFilterTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long countWithRepeatedProcessDefinitionKey(
                        String firstProcessDefinitionKey, String secondProcessDefinitionKey) {
                    return camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId(secondProcessDefinitionKey)
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
  void preservesIntResultInStrictContextsInsideLongDeclarations() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;

            public class ProcessInstanceQueryStrictContextTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public long[] arrayDimension() {
                    long[] values = new long[engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()];
                    return values;
                }

                public long switchSelector() {
                    long result = switch (engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .size()) {
                        case 0 -> 0L;
                        default -> 1L;
                    };
                    return result;
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;

            public class ProcessInstanceQueryStrictContextTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long[] arrayDimension() {
                    long[] values = new long[camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()];
                    return values;
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long switchSelector() {
                    long result = switch (camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().intValue()) {
                        case 0 -> 0L;
                        default -> 1L;
                    };
                    return result;
                }
            }
            """));
  }

  @Test
  void doesNotFlagShadowedOrReassignedQueryResultVariables() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.ArrayList;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class QueryResultTrackingScopeTestClass {

                private List<?> instances;
                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public void loadFieldQueryResult() {
                    instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                }

                public int shadowedFieldQueryResult(List<?> instances) {
                    return instances.size();
                }

                public void resetFieldQueryResult() {
                    instances = new ArrayList<>();
                }

                public int fieldQueryResult() {
                    return instances.size();
                }

                public int reassignedQueryResult() {
                    List<?> instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                    instances = new ArrayList<>();
                    return instances.size();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import io.camunda.client.api.search.response.ProcessInstance;

            import java.util.ArrayList;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class QueryResultTrackingScopeTestClass {

                private List<?> instances;
                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public void loadFieldQueryResult() {
                    instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                }

                public int shadowedFieldQueryResult(List<?> instances) {
                    return instances.size();
                }

                public void resetFieldQueryResult() {
                    instances = new ArrayList<>();
                }

                public int fieldQueryResult() {
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }

                public int reassignedQueryResult() {
                    List<ProcessInstance> instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    instances = new ArrayList<>();
                    return instances.size();
                }
            }
            """));
  }

  @Test
  void keepsPossibleQueryResultAfterConditionalReassignment() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.ArrayList;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class ConditionalQueryResultTrackingTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public int countAfterConditionalReassignment(boolean reset) {
                    List<?> instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                    if (reset) {
                        instances = new ArrayList<>();
                    }
                    return instances.size();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import io.camunda.client.api.search.response.ProcessInstance;

            import java.util.ArrayList;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class ConditionalQueryResultTrackingTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public int countAfterConditionalReassignment(boolean reset) {
                    List<ProcessInstance> instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    if (reset) {
                        instances = new ArrayList<>();
                    }
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }
            }
            """));
  }

  @Test
  void flagsDerivedProcessInstanceStreamCounts() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class DerivedProcessInstanceStreamCountTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public long directDerivedCount() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list()
                            .stream()
                            .filter(instance -> true)
                            .count();
                }

                public long assignedDerivedCount() {
                    List<?> instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                    return instances.stream()
                            .filter(instance -> true)
                            .count();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import io.camunda.client.api.search.response.ProcessInstance;

            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class DerivedProcessInstanceStreamCountTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public long directDerivedCount() {
                    return /* TODO: Manual migration required - preserve the complete query count before applying stream operations to the paginated result. */ camundaClient
                                    .newProcessInstanceSearchRequest()
                                    .filter(filter -> filter
                                            .processDefinitionId("order-process")
                                            .state(ProcessInstanceState.ACTIVE))
                                    .send()
                                    .join()
                                    .items()
                            .stream()
                            .filter(instance -> true)
                            .count();
                }

                public long assignedDerivedCount() {
                    List<ProcessInstance> instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                    return /* TODO: Manual migration required - preserve the complete query count before applying stream operations to the paginated result. */ instances.stream()
                            .filter(instance -> true)
                            .count();
                }
            }
            """));
  }

  @Test
  void tracksFieldQueryResultsBeforeTheirAssignments() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class FieldQueryResultOrderTestClass {

                private List<?> instances;
                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public int countFieldBeforeLoad() {
                    return instances.size();
                }

                public void loadField() {
                    instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;

            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class FieldQueryResultOrderTestClass {

                private List<?> instances;
                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public int countFieldBeforeLoad() {
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ instances.size();
                }

                public void loadField() {
                    instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                }
            }
            """));
  }

  @Test
  void warnsForCountInFieldInitializer() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.ProcessEngine;

            public class FieldInitializerCountTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;
                private int count = engine.getRuntimeService()
                        .createProcessInstanceQuery()
                        .active()
                        .list()
                        .size();
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;
            import org.camunda.bpm.engine.ProcessEngine;

            /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
            public class FieldInitializerCountTestClass {

                private ProcessEngine engine;
                private CamundaClient camundaClient;
                private int count = camundaClient
                        .newProcessInstanceSearchRequest()
                        .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join()
                        .page()
                        .totalItems().intValue();
            }
            """));
  }

  @Test
  void keepsQueryResultTrackingReceiverSpecificForFields() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class QueryResultFieldReceiverTestClass {

                private List<?> instances;
                private OtherHolder other;
                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public void loadFieldQueryResult() {
                    instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey("order-process")
                            .active()
                            .list();
                }

                public int countThisField() {
                    return this.instances.size();
                }

                public int countOtherField() {
                    return other.instances.size();
                }
            }

            class OtherHolder {
                private List<?> instances;
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.api.search.enums.ProcessInstanceState;

            import java.util.List;
            import org.camunda.bpm.engine.ProcessEngine;

            public class QueryResultFieldReceiverTestClass {

                private List<?> instances;
                private OtherHolder other;
                private ProcessEngine engine;
                private CamundaClient camundaClient;

                public void loadFieldQueryResult() {
                    instances = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("order-process")
                                    .state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items();
                }

                public int countThisField() {
                    return /* TODO: Manual migration required - use page().totalItems() for the complete query count of: instances. Check page().hasMoreTotalItems() because totalItems() can be a lower bound. */ this.instances.size();
                }

                public int countOtherField() {
                    return other.instances.size();
                }
            }

            class OtherHolder {
                private List<?> instances;
            }
            """));
  }

  @Test
  void doesNotRewriteCountsForPreconfiguredProcessInstanceQuery() {
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
            public class PreconfiguredProcessInstanceQueryTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countPreconfiguredQuery(ProcessInstanceQuery query) {
                    engine.getRuntimeService().createProcessInstanceQuery();
                    return query.list().size();
                }

                public long streamCountPreconfiguredQuery(ProcessInstanceQuery query) {
                    return query.list().stream().count();
                }

                public long directCountPreconfiguredQuery(ProcessInstanceQuery query) {
                    return query.count();
                }

                public int countPreconfiguredFilteredQuery(
                        ProcessInstanceQuery query, String processDefinitionKey) {
                    return query.processDefinitionKey(processDefinitionKey).list().size();
                }

                public long directCountPreconfiguredFilteredQuery(
                        ProcessInstanceQuery query, String processDefinitionKey) {
                    return query.processDefinitionKey(processDefinitionKey).count();
                }
            }
            """));
  }

  @Test
  void doesNotChangeTypesForUnsupportedProcessInstanceFilters() {
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
            public class UnsupportedProcessInstanceListTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public List<ProcessInstance> findSuspended(String activityId) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityId)
                            .suspended()
                            .list();
                }

                public List<ProcessInstance> findSuspendedByProcessDefinitionKey(
                        String processDefinitionKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .suspended()
                            .list();
                }
            }
            """));
  }

  @Test
  void doesNotRewriteCountsWithUnsupportedProcessInstanceFilters() {
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
            public class UnsupportedProcessInstanceFilterTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public int countSuspended(String activityId) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityId)
                            .suspended()
                            .list()
                            .size();
                }

                public int countSuspendedWithProcessDefinitionKey(String processDefinitionKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .suspended()
                            .list()
                            .size();
                }

                public long directCountSuspendedWithProcessDefinitionKey(
                        String processDefinitionKey) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .processDefinitionKey(processDefinitionKey)
                            .suspended()
                            .count();
                }

                public long directCountMultipleActivityIds(String firstActivityId, String secondActivityId) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(firstActivityId, secondActivityId)
                            .count();
                }

                public int listSizeMultipleActivityIds(String firstActivityId, String secondActivityId) {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(firstActivityId, secondActivityId)
                            .list()
                            .size();
                }
            }
            """));
  }

  @Test
  void doesNotRewriteActivityIdInFiltersWithoutSupportedC8Mapping() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class UnsupportedActivityIdInFilterTestClass {

                @Autowired
                private ProcessEngine engine;

                public void queryByActivityId(String[] activityIds) {
                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityIds)
                            .active()
                            .list();

                    engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .activityIdIn(activityIds)
                            .active()
                            .count();
                }
            }
            """));
  }

  @Test
  void doesNotRewriteGenuinelyUnfilteredProcessInstanceCounts() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            import java.util.List;

            @Component
            public class GenuinelyUnfilteredProcessInstanceCountsTestClass {

                @Autowired
                private ProcessEngine engine;

                public int listSize() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .list()
                            .size();
                }

                public long streamCount() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .list()
                            .stream()
                            .count();
                }

                public int assignedListSize() {
                    List<?> instances = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .list();
                    return instances.size();
                }

                public long directCount() {
                    return engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .count();
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            import java.util.List;

            @Component
            public class GenuinelyUnfilteredProcessInstanceCountsTestClass {

                @Autowired
                private ProcessEngine engine;

                public int listSize() {
                    return /* TODO: Manual migration required - preserve the unfiltered process-instance count, including suspended instances, before migration. */ engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .list()
                            .size();
                }

                public long streamCount() {
                    return /* TODO: Manual migration required - preserve the unfiltered process-instance count, including suspended instances, before migration. */ engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .list()
                            .stream()
                            .count();
                }

                public int assignedListSize() {
                    List<?> instances = /* TODO: Manual migration required - preserve unfiltered process-instance query semantics, including suspended instances, before migration. */ engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .list();
                    return instances.size();
                }

                public long directCount() {
                    return /* TODO: Manual migration required - preserve the unfiltered process-instance count, including suspended instances, before migration. */ engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .count();
                }
            }
            """));
  }

  @Test
  void preservesFollowingVariablesForCountExpressions() {
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
            public class MultipleCountVariablesTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                public long directCount() {
                    long count = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .count(), other = 0;
                    return count + other;
                }

                public long streamCount() {
                    long count = engine.getRuntimeService()
                            .createProcessInstanceQuery()
                            .active()
                            .list()
                            .stream()
                            .count(), other = 0;
                    return count + other;
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
            public class MultipleCountVariablesTestClass {

                @Autowired
                private ProcessEngine engine;

                @Autowired
                private CamundaClient camundaClient;

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long directCount() {
                    long count = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue(), other = 0;
                    return count + other;
                }

                /* TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound. */
                public long streamCount() {
                    long count = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                            .send()
                            .join()
                            .page()
                            .totalItems().longValue(), other = 0;
                    return count + other;
                }
            }
            """));
  }
}
