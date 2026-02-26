/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate.prepare;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.delegate.PrepareJobWorkerBeneathExecutionListenerRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class InsertJobWorkerForExecutionListenerTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new PrepareJobWorkerBeneathExecutionListenerRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void injectJobWorkerForExecutionListener() {
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
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MyExecutionListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        String someVar = (String) execution.getVariable("foo");
    }

    @JobWorker(type = "myExecutionListener", autoComplete = true)
    public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;
    }
}
            """));
  }
}
