package io.camunda.migration.data.qa.history.entity.delegate;

import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class MissingFormDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    FormService formService = execution.getProcessEngineServices().getFormService();
    // This will throw NotFoundException
    formService.getDeployedStartForm(execution.getProcessDefinitionId());
  }
}