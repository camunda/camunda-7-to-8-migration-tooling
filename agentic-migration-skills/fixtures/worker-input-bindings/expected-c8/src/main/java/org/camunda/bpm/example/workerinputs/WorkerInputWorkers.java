/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import org.springframework.stereotype.Component;

@Component
public class WorkerInputWorkers {

  private final WorkerInputRecorder recorder;
  private final ProjectPersistenceDelegate projectDelegate;

  public WorkerInputWorkers(
      WorkerInputRecorder recorder, ProjectPersistenceDelegate projectDelegate) {
    this.recorder = recorder;
    this.projectDelegate = projectDelegate;
  }

  @JobWorker(type = "loan-granting")
  public void loanGranting(@Variable(name = "defaultScore") int defaultScore) {
    recorder.record("defaultScore", defaultScore);
  }

  @JobWorker(type = "synchronous-service-task")
  public void synchronousServiceTask(@Variable(name = "shouldFail") boolean shouldFail) {
    recorder.record("shouldFail", shouldFail);
    if (shouldFail) {
      throw new IllegalStateException("The source requested a failure");
    }
  }

  @JobWorker(type = "twitter")
  public void twitter(@Variable(name = "content", optional = true) String content) {
    recorder.record("content", content);
  }

  @JobWorker(type = "persist-project", fetchAllVariables = true)
  public void persistProject(ActivatedJob job) {
    projectDelegate.persist(job.getVariablesAsMap());
  }
}
