/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ExecutionListenerToJobWorkerSpringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("io.camunda.migration.code.recipes.AllDelegateRecipes")
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void rewriteExecutionListener() {
    rewriteRun(
        java(
"""
package org.camunda.conversion.execution_listeners;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class MyExecutionListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        String someVar = (String) execution.getVariable("foo");
    }
}
""",
"""
package org.camunda.conversion.execution_listeners;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MyExecutionListener {

    @JobWorker(type = "myExecutionListener", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        String someVar = (String) job.getVariable("foo");
        return resultMap;
    }
}
"""));
    }

    @Test
    void executionAccessTest() {
    rewriteRun(
        java(
"""
package org.camunda.conversion.execution_listeners;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.stereotype.Component;

import static org.camunda.spin.Spin.JSON;

@Component("TestExecutionListener")
public class TestExecutionListener implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) {

        System.out.println("C7 execution listener called");
        final var stringVariable = execution.getVariable("stringVariable");
        final var integerVariable = execution.getVariable("integerVariable");
        final var doubleVariable = execution.getVariable("doubleVariable");
        final var boolVariable = execution.getVariable("boolVariable");
        final var jsonVariable = execution.getVariable("jsonVariable");
        final var fileVariable = execution.getVariable("fileVariable");
        final TypedValue stringVariableTyped = execution.getVariableTyped("stringVariable");
        final TypedValue integerVariableTyped = execution.getVariableTyped("integerVariable");
        final TypedValue doubleVariableTyped = execution.getVariableTyped("doubleVariable");
        final TypedValue boolVariableTyped = execution.getVariableTyped("boolVariable");
        final TypedValue jsonVariableTyped = execution.getVariableTyped("jsonVariable");
        final TypedValue fileVariableTyped = execution.getVariableTyped("fileVariable");
        final var stringVariableLocal = execution.getVariableLocal("stringVariableLocal");
        final var integerVariableLocal = execution.getVariableLocal("integerVariableLocal");
        final var doubleVariableLocal = execution.getVariableLocal("doubleVariableLocal");
        final var boolVariableLocal = execution.getVariableLocal("boolVariableLocal");
        final var jsonVariableLocal = execution.getVariableLocal("jsonVariableLocal");

        final String procInstanceId = execution.getProcessInstanceId();
        final String procDefId = execution.getProcessDefinitionId();
        final String curActId = execution.getCurrentActivityId();
        final String actInstanceId = execution.getActivityInstanceId();

        execution.setVariable("newStringVariable", "Warum");
        execution.setVariable("newIntegerVariable", 42);
        execution.setVariable("newDoubleVariable", 2.71828);
        execution.setVariable("newBoolVariable", true);
        execution.setVariable("newJsonVariable", JSON("{\\"key1\\" : \\"value1\\"}"));
        execution.setVariableLocal("newStringVariableLocal", "Wieso");
        execution.setVariableLocal("newIntegerVariableLocal", 4711);
        execution.setVariableLocal("newDoubleVariableLocal", 6.626);
        execution.setVariableLocal("newBoolVariableLocal", false);
        execution.setVariableLocal("newJsonVariableLocal", JSON("{\\"key2\\" : \\"value2\\"}"));

        execution.setVariable("newObjectVariable",
                new DummyClass(215, 9.81, "Ein Beispielstring zum testen"));
        System.out.println("C7 finished");
    }
    record DummyClass(Integer zahl, Double nochneZahl, String einString){}
}
""",
"""
package org.camunda.conversion.execution_listeners;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.spin.Spin.JSON;

@Component("TestExecutionListener")
public class TestExecutionListener {
    record DummyClass(Integer zahl, Double nochneZahl, String einString){}

    @JobWorker(type = "testExecutionListener", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();

        System.out.println("C7 execution listener called");
        final var stringVariable = job.getVariable("stringVariable");
        final var integerVariable = job.getVariable("integerVariable");
        final var doubleVariable = job.getVariable("doubleVariable");
        final var boolVariable = job.getVariable("boolVariable");
        final var jsonVariable = job.getVariable("jsonVariable");
        final var fileVariable = job.getVariable("fileVariable");
        // please check type
        final Object stringVariableTyped = job.getVariable("stringVariable");
        // please check type
        final Object integerVariableTyped = job.getVariable("integerVariable");
        // please check type
        final Object doubleVariableTyped = job.getVariable("doubleVariable");
        // please check type
        final Object boolVariableTyped = job.getVariable("boolVariable");
        // please check type
        final Object jsonVariableTyped = job.getVariable("jsonVariable");
        // please check type
        final Object fileVariableTyped = job.getVariable("fileVariable");
        final var stringVariableLocal = job.getVariable("stringVariableLocal");
        final var integerVariableLocal = job.getVariable("integerVariableLocal");
        final var doubleVariableLocal = job.getVariable("doubleVariableLocal");
        final var boolVariableLocal = job.getVariable("boolVariableLocal");
        final var jsonVariableLocal = job.getVariable("jsonVariableLocal");

        final String procInstanceId = String.valueOf(job.getProcessInstanceKey());
        final String procDefId = String.valueOf(job.getProcessDefinitionKey());
        final String curActId = job.getElementId();
        final String actInstanceId = String.valueOf(job.getElementInstanceKey());

        resultMap.put("newStringVariable", "Warum");
        resultMap.put("newIntegerVariable", 42);
        resultMap.put("newDoubleVariable", 2.71828);
        resultMap.put("newBoolVariable", true);
        resultMap.put("newJsonVariable", JSON("{\\"key1\\" : \\"value1\\"}"));
        resultMap.put("newStringVariableLocal", "Wieso");
        resultMap.put("newIntegerVariableLocal", 4711);
        resultMap.put("newDoubleVariableLocal", 6.626);
        resultMap.put("newBoolVariableLocal", false);
        resultMap.put("newJsonVariableLocal", JSON("{\\"key2\\" : \\"value2\\"}"));

        resultMap.put("newObjectVariable", new DummyClass(215, 9.81, "Ein Beispielstring zum testen"));
        System.out.println("C7 finished");
        return resultMap;
    }
}
"""));
    }
}

