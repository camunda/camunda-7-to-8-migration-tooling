package org.camunda.example;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    LOG.info("Running activity {}", execution.getCurrentActivityId());
  }
}
