/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;

public class TwitterWorker implements ExternalTaskHandler {

  private final OfflinePublisher publisher;

  public TwitterWorker(OfflinePublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void execute(ExternalTask task, ExternalTaskService service) {
    String content = (String) task.getVariable("content");
    publisher.publish(content);
    service.complete(task);
  }

  public interface OfflinePublisher {
    void publish(String content);
  }
}
