/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProjectPersistenceDelegate {

  private final WorkerInputRecorder recorder;

  public ProjectPersistenceDelegate(WorkerInputRecorder recorder) {
    this.recorder = recorder;
  }

  public void persist(Map<String, Object> variables) {
    recorder.record("customerId", variables.get("customerId"));
    recorder.record("projectName", variables.get("projectName"));
  }
}
