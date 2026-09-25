package org.example;

import java.util.Date;
import org.camunda.bpm.engine.ManagementService;

public class TimerDueDateUpdater {

  private final ManagementService managementService;

  public TimerDueDateUpdater(ManagementService managementService) {
    this.managementService = managementService;
  }

  public void updateForProcessInstance(String processInstanceId, Date dueDate) {
    managementService.createJobQuery().processInstanceId(processInstanceId).timers().list().stream()
        .forEach(job -> managementService.setJobDuedate(job.getId(), dueDate));
  }
}
