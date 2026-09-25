package org.example;

import java.util.Date;

public class ChangeProjectTerminationDateDelegate {

  private final TimerDueDateUpdater timerDueDateUpdater;

  public ChangeProjectTerminationDateDelegate(TimerDueDateUpdater timerDueDateUpdater) {
    this.timerDueDateUpdater = timerDueDateUpdater;
  }

  public void changeDate(String processInstanceId, Date dueDate) {
    timerDueDateUpdater.updateForProcessInstance(processInstanceId, dueDate);
    timerDueDateUpdater.updateForProcessInstance(processInstanceId, dueDate);
  }
}
