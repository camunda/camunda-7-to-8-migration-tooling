package io.camunda.migration.code.recipes.delegate.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.delegate.MigrateExecutionRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ReplaceExecutionListenerTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new MigrateExecutionRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void copyNotifyLogicIntoJobWorker() {
    rewriteRun(
        java(
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
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        String someVar = (String) job.getVariable("foo");
        return resultMap;
    }
}
            """));
  }
}
