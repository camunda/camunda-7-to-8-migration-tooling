package io.camunda.migration.data.qa.history.entity.delegate;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class ConditionalEvaluationDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
    // This will throw ProcessEngineException with "No subscriptions were found during evaluation of the conditional start events"
    runtimeService.createConditionEvaluation().setVariable("foo", 1).evaluateStartConditions();
  }
}