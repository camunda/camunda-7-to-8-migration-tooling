/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkerInputRecorder {

  private final Map<String, Object> inputs = Collections.synchronizedMap(new HashMap<>());

  public void record(String name, Object value) {
    inputs.put(name, value);
  }

  public Map<String, Object> snapshot() {
    synchronized (inputs) {
      return new HashMap<>(inputs);
    }
  }

  public void clear() {
    inputs.clear();
  }
}
