# Deployment and timer preflight fixture

This fixture evaluates the `migrate-c7-to-c8-code` skill's deployment-set and timer preflight.
It contains a recurring timer start, duplicate process IDs, a `latestVersion()` caller, and active
timer due-date update code with more than one caller.

## Fixture layout

```text
deployment-set/
  pom.xml
  loan-module/src/main/resources/loan-cycle.bpmn
  order-module/src/main/resources/order-cycle.bpmn
  starter-call/SampleStarter.java
active-timer-update/
  project-termination.bpmn
  src/main/java/org/example/TimerDueDateUpdater.java
  src/main/java/org/example/ChangeProjectTerminationDateDelegate.java
  src/main/java/org/example/ProjectAdministrationService.java
verify_timer_preflight.py
```

The Maven reactor contains two process modules. Each module defines process ID `Sample`. The loan
process has a recurring timer start with cycle `R/PT5S`. The starter call uses
`.bpmnProcessId("Sample").latestVersion()`.
The `starter-call` source probe sits outside the two-module Maven reactor.

The active-timer case has a timer boundary event on the project activity. A Camunda 7 helper changes
timer due dates for an active process instance. The project-date delegate calls the helper twice.
The administration service calls the same helper once.

Camunda 8 schedules timer start events on deployment. Each timer firing creates a process instance.
An unbounded time cycle repeats indefinitely. See the
[timer events documentation](https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/).

## Run the fixture checks

1. Run the dependency-free structural check:

   ```sh
   python3 verify_timer_preflight.py
   ```

2. Validate the multi-module Maven structure:

   ```sh
   mvn -f deployment-set/pom.xml validate
   ```

3. Copy `deployment-set` and `active-timer-update` into a temporary project. Run the migration skill
   with **Code + models** and target **Camunda 8.9**.
4. Confirm that `MIGRATION_REPORT.md` lists the `R/PT5S` automatic-start effect, both `Sample`
   definitions, the `.latestVersion()` caller, the timer helper, and every helper caller.
5. Confirm that the skill requests a decision for the recurring timer and duplicate process ID before
   deployment. Confirm that it keeps the active timer due-date update blocking until the project
   approves and tests a target-supported alternative.
6. Confirm that the skill does not leave a reachable `UnsupportedOperationException` placeholder
   while reporting readiness.

## Disposable-target tests

Never deploy this fixture to a shared target to validate syntax. The five-second cycle can create
process instances repeatedly after deployment.

When a runtime test is approved, use a disposable Camunda 8 target. Record its version, deployment
set, test window, and cleanup plan before deployment. The plan must remove every timer-created
instance and destroy or reset the target. Record the deployment identifier, observed instances, and
completed cleanup in `MIGRATION_REPORT.md`.

For the active-timer case, start an instance and confirm that the termination timer is active.
Record its original due date. Apply an approved due-date change, then change it again on the same
instance. Assert that obsolete deadlines do not fire and that the final deadline fires only once.
Allow for asynchronous execution after the due date. Record the target version and observed times.

No Camunda cluster deployment is part of the structural check. If no target-supported replacement is
approved, record the active-timer test as `not run` and keep the flow as blocking manual work.
