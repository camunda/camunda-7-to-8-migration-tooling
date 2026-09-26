/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkerInputBindingSignatureTest {

  @Test
  void everySingleVariableBindingNamesItsInput() {
    int workerCount = 0;

    for (Method method : WorkerInputWorkers.class.getDeclaredMethods()) {
      if (!method.isAnnotationPresent(JobWorker.class)) {
        continue;
      }
      workerCount++;

      for (Parameter parameter : method.getParameters()) {
        Variable variable = parameter.getAnnotation(Variable.class);
        if (variable == null || Map.class.isAssignableFrom(parameter.getType())) {
          continue;
        }

        assertThat(variable.name())
            .as("%s must name each process variable explicitly", method.getName())
            .isNotBlank();
      }
    }

    assertThat(workerCount).isPositive();
  }

  @Test
  void noWorkerInjectsTheWholeVariableMapWithVariable() {
    for (Method method : WorkerInputWorkers.class.getDeclaredMethods()) {
      if (!method.isAnnotationPresent(JobWorker.class)) {
        continue;
      }
      for (Parameter parameter : method.getParameters()) {
        if (Map.class.isAssignableFrom(parameter.getType())) {
          Variable variable = parameter.getAnnotation(Variable.class);
          if (variable != null) {
            assertThat(variable.name())
                .as("%s must not inject the complete map as one variable", method.getName())
                .isNotBlank()
                .isNotEqualTo("variables");
          }
        }
      }
    }
  }

  @Test
  void completeMapWorkerUsesActivatedJobAndFetchesEveryVariable() throws NoSuchMethodException {
    Method method = WorkerInputWorkers.class.getDeclaredMethod("persistProject", ActivatedJob.class);
    JobWorker worker = method.getAnnotation(JobWorker.class);

    assertThat(Arrays.asList(method.getParameterTypes())).contains(ActivatedJob.class);
    assertThat(worker.fetchAllVariables()).contains(true);
  }

  @Test
  void fixtureCompilesWithoutRetainedParameterNames() {
    for (Method method : WorkerInputWorkers.class.getDeclaredMethods()) {
      if (!method.isAnnotationPresent(JobWorker.class)) {
        continue;
      }
      for (Parameter parameter : method.getParameters()) {
        assertThat(parameter.isNamePresent())
            .as("%s must compile without retained parameter names", method.getName())
            .isFalse();
      }
    }
  }
}
