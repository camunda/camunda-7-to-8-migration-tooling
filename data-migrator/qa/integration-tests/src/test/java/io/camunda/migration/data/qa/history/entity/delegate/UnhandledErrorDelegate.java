package io.camunda.migration.data.qa.history.entity.delegate;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class UnhandledErrorDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    // This BpmnError will be propagated and since there is no error catcher the engine will treat it as an unhandled error event
    throw new BpmnError("UNHANDLED_ERROR_CODE", "Simulated unhandled BPMN error");
  }
}