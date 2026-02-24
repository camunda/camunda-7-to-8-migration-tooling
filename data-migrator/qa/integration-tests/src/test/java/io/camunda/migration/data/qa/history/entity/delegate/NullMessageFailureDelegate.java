package io.camunda.migration.data.qa.history.entity.delegate;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class NullMessageFailureDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    // Real engine exception, but with null message so incidentMessage will be null
    throw new ProcessEngineException((String) null);
  }
}
