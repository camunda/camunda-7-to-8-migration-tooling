package org.example;

import java.util.Date;

public class ProjectAdministrationService {

  private final TimerDueDateUpdater timerDueDateUpdater;

  public ProjectAdministrationService(TimerDueDateUpdater timerDueDateUpdater) {
    this.timerDueDateUpdater = timerDueDateUpdater;
  }

  public void changeDeadline(String processInstanceId, Date dueDate) {
    timerDueDateUpdater.updateForProcessInstance(processInstanceId, dueDate);
  }
}
