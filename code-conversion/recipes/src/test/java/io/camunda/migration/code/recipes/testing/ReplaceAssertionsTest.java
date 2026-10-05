/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.testing;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

public class ReplaceAssertionsTest implements RewriteTest {

    @Test
    void replaceTestingMethodsTest() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.RuntimeService;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.boot.test.context.SpringBootTest;
                                import org.junit.jupiter.api.Test;

                                import java.util.Map;

                                import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;

                                @SpringBootTest
                                public class Testcases {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private RuntimeService runtimeService;

                                    @Test
                                    void somePath() {
                                        ProcessInstance processInstance = runtimeService
                                                 .startProcessInstanceByKey(
                                                         "sample-process-solution-process",
                                                         Map.ofEntries(Map.entry("x", 7))
                                                 );
                                        assertThat(processInstance).isWaitingAt("xxx");
                                        assertThat(processInstance).isEnded().hasPassed("yyy");
                                    }
                                }
                                """,
            """
                                package org.camunda.community.migration.example;
                                import io.camunda.client.api.response.ProcessInstanceEvent;
                                import org.camunda.bpm.engine.RuntimeService;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.boot.test.context.SpringBootTest;
                                import org.junit.jupiter.api.Test;

                                import java.util.Map;

                                import static io.camunda.process.test.api.CamundaAssert.assertThat;

                                @SpringBootTest
                                public class Testcases {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private RuntimeService runtimeService;

                                    @Test
                                    void somePath() {
                                        ProcessInstanceEvent processInstance = camundaClient
                                                .newCreateInstanceCommand()
                                                .bpmnProcessId("sample-process-solution-process")
                                                .latestVersion()
                                                .variables(Map.ofEntries(Map.entry("x", 7)))
                                                .send()
                                                .join();
                                        assertThat(processInstance).hasActiveElements("xxx");
                                        assertThat(processInstance).isCompleted().hasCompletedElements("yyy");
                                    }
                                }
                                """));
    }

    @Test
    void replaceTaskTestingMethodsTest() {
        rewriteRun(
                spec ->
                        spec.recipeFromResources(
                                "io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
                // language=java
                java(
                        """
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;

@SpringBootTest
public class Testcases {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;

    @Test
    void somePath(Date someDate) {
        ProcessInstance processInstance = runtimeService
                 .startProcessInstanceByKey(
                         "sample-process-solution-process",
                         Map.ofEntries(Map.entry("x", 7))
                 );
                 
        List<Task> userTasks = taskService.createTaskQuery()
                .processDefinitionKey("sample-process-solution-process")
                .dueBefore(someDate)
                .list();
                
        Task firstTask = userTasks.get(0);

        assertThat(processInstance).isWaitingAt("xxx");
        assertThat(firstTask).isAssignedTo("John Doe");
        assertThat(processInstance).isEnded().hasPassed("yyy");
        assertThat(processInstance).variables().containsEntry("a", "b");
    }
}
""",
                        """
package org.camunda.community.migration.example;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.UserTask;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

@SpringBootTest
public class Testcases {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;

    @Test
    void somePath(Date someDate) {
        ProcessInstanceEvent processInstance = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("sample-process-solution-process")
                .latestVersion()
                .variables(Map.ofEntries(Map.entry("x", 7)))
                .send()
                .join();
                
        List<UserTask> userTasks = camundaClient
                .newUserTaskSearchRequest()
                .filter(filter -> filter.bpmnProcessId("sample-process-solution-process")
                        .dueDate(dateTimeProperty -> dateTimeProperty.lt(someDate.toInstant().atOffset(ZoneOffset.UTC))))
                .send()
                .join()
                .items();

        UserTask firstTask = userTasks.get(0);
        
        assertThat(processInstance).hasActiveElements("xxx");
        assertThat(byTaskName(firstTask.getName())).hasAssignee("John Doe");
        assertThat(processInstance).isCompleted().hasCompletedElements("yyy");
        assertThat(processInstance).isCreated().hasVariable("a", "b");
    }
}
                                            """));
    }

    @Test
    void replaceBpmnAwareTestsStaticImportTest() {
        rewriteProcessInstanceAssertion(
                "import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;",
                "assertThat(processInstance)");
    }

    @Test
    void replaceBpmnAwareTestsWildcardImportTest() {
        rewriteProcessInstanceAssertion(
                "import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;",
                "assertThat(processInstance)");
    }

    @Test
    void replaceQualifiedBpmnAwareTestsCallTest() {
        rewriteProcessInstanceAssertion(
                "import org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests;",
                "BpmnAwareTests.assertThat(processInstance)");
    }

    @Test
    void replaceQualifiedProcessEngineTestsCallTest() {
        rewriteProcessInstanceAssertion(
                "import org.camunda.bpm.engine.test.assertions.ProcessEngineTests;",
                "ProcessEngineTests.assertThat(processInstance)");
    }

    @Test
    void replaceProcessEngineTestsWildcardImportTest() {
        rewriteProcessInstanceAssertion(
                "import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;",
                "assertThat(processInstance)");
    }

    @Test
    void replaceCmmnAwareTestsStaticImportTest() {
        rewriteProcessInstanceAssertion(
                "import static org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests.assertThat;",
                "assertThat(processInstance)");
    }

    @Test
    void replaceBpmnAwareTestsTaskStaticImportTest() {
        rewriteTaskAssertion(
                "import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;",
                "assertThat");
    }

    @Test
    void replaceBpmnAwareTestsTaskWildcardImportTest() {
        rewriteTaskAssertion(
                "import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;",
                "assertThat");
    }

    @Test
    void replaceQualifiedBpmnAwareTestsTaskCallTest() {
        rewriteTaskAssertion(
                "import org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests;",
                "BpmnAwareTests.assertThat");
    }

    @Test
    void replaceQualifiedProcessEngineTestsTaskCallTest() {
        rewriteTaskAssertion(
                "import org.camunda.bpm.engine.test.assertions.ProcessEngineTests;",
                "ProcessEngineTests.assertThat");
    }

    @Test
    void keepBpmnAwareTestsWildcardImportWhenCompleteIsUsed() {
        rewriteRun(
                spec ->
                        spec.recipeFromResources(
                                "io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
                java(
                        """
package org.example;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

class OrderTaskTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private TaskService taskService;

    void taskAssignment(Date someDate) {
        List<Task> userTasks = taskService.createTaskQuery()
                .processDefinitionKey("order")
                .dueBefore(someDate)
                .list();

        Task firstTask = userTasks.get(0);

        complete(firstTask);
        assertThat(firstTask).isAssignedTo("John Doe");
    }
}
""",
                        """
package org.example;
import io.camunda.client.api.search.response.UserTask;
import org.camunda.bpm.engine.TaskService;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

class OrderTaskTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private TaskService taskService;

    void taskAssignment(Date someDate) {
        List<UserTask> userTasks = camundaClient
                .newUserTaskSearchRequest()
                .filter(filter -> filter.bpmnProcessId("order")
                        .dueDate(dateTimeProperty -> dateTimeProperty.lt(someDate.toInstant().atOffset(ZoneOffset.UTC))))
                .send()
                .join()
                .items();

        UserTask firstTask = userTasks.get(0);

        complete(firstTask);
        assertThat(byTaskName(firstTask.getName())).hasAssignee("John Doe");
    }
}
"""));
    }

    private void rewriteProcessInstanceAssertion(String assertionsImport, String assertion) {
        rewriteRun(
                spec ->
                        spec.recipeFromResources(
                                "io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
                java(
                        """
package org.example;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;

%s

class OrderProcessTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RuntimeService runtimeService;

    void approvalPath() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("order");
        %s.isWaitingAt("Task_Approve");
    }
}
""".formatted(assertionsImport, assertion),
                        """
package org.example;
import io.camunda.client.api.response.ProcessInstanceEvent;
import org.camunda.bpm.engine.RuntimeService;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

class OrderProcessTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RuntimeService runtimeService;

    void approvalPath() {
        ProcessInstanceEvent processInstance = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("order")
                .latestVersion()
                .send()
                .join();
        assertThat(processInstance).hasActiveElements("Task_Approve");
    }
}
"""));
    }

    private void rewriteTaskAssertion(String assertionsImport, String assertion) {
        rewriteRun(
                spec ->
                        spec.recipeFromResources(
                                "io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
                java(
                        """
package org.example;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

%s

class OrderTaskTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private TaskService taskService;

    void taskAssignment(Date someDate) {
        List<Task> userTasks = taskService.createTaskQuery()
                .processDefinitionKey("order")
                .dueBefore(someDate)
                .list();

        Task firstTask = userTasks.get(0);

        %s(firstTask).isAssignedTo("John Doe");
    }
}
""".formatted(assertionsImport, assertion),
                        """
package org.example;
import io.camunda.client.api.search.response.UserTask;
import org.camunda.bpm.engine.TaskService;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

class OrderTaskTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private TaskService taskService;

    void taskAssignment(Date someDate) {
        List<UserTask> userTasks = camundaClient
                .newUserTaskSearchRequest()
                .filter(filter -> filter.bpmnProcessId("order")
                        .dueDate(dateTimeProperty -> dateTimeProperty.lt(someDate.toInstant().atOffset(ZoneOffset.UTC))))
                .send()
                .join()
                .items();

        UserTask firstTask = userTasks.get(0);

        assertThat(byTaskName(firstTask.getName())).hasAssignee("John Doe");
    }
}
"""));
    }
}
