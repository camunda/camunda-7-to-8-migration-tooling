package io.camunda.conversion.process_instance;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class StartProcessInstance {

  @Autowired
  private ZeebeClient zeebeClient;

  public void startProcess(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    zeebeClient.newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(variables)
            .send()
            .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
  }

  public void startProcessByKeyAssignedOnDeployment(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    zeebeClient.newCreateInstanceCommand()
            .processDefinitionKey(21653461)
            .variables(variables)
            .send()
            .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
  }
}
