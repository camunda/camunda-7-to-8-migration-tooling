package org.camunda.conversion.start_process_instance;

import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class StartProcessInstance {

  @Autowired
  private ProcessEngine camunda;

  public void startProcessByProcessIdentifier(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    camunda.getRuntimeService().startProcessInstanceByKey("order", variables);
  }

  public void startProcessByBPMNModelIdentifier(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    camunda.getRuntimeService().startProcessInstanceById("orderProcess", variables);
  }
}
