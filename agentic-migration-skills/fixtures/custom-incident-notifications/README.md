# Custom Incident Notifications

This fixture checks that the migration flow keeps custom incident notifications separate from job-worker migration.

## Source behavior

The Camunda 7 application has two classes:

| Source file | Behavior |
|---|---|
| `incident/IncidentHandlerProcessEnginePlugin.java` | Registers `ErrorNotificationIncidentHandler`. |
| `incident/ErrorNotificationIncidentHandler.java` | Creates an incident for a failed job and sends an error notification to configured recipients. |

The incident and notification are separate observable behaviors. Both belong to the application's operational behavior.

## Expected assessment

The migration report must list a separate incident-notification finding. It must record the handler registration, trigger, recipient configuration, useful message context, duplicate-handling behavior, and data exposed by the notification.

The migration flow must ask the project owner to choose one path:

| Project decision | Expected result |
|---|---|
| Implement a Camunda 8-compatible integration | Keep the flow blocked until disposable-target verification passes. |
| Waive notification behavior | Record the approver, date, reason, accepted behavior loss, and decision reference. Resolve the finding as `waived`, not `verified`. |
| No decision recorded | Mark the finding `blocked`, add an open report item, and stop before conversion or deployment. |

The finding is not resolved because a worker registers, a Camunda 8 incident appears, or Operate displays the incident.

## Disposable-target verification

Run this test only after the project approves an integration:

1. Use a disposable Camunda 8 target and synthetic data.
2. Start a process with a test job worker that fails with zero remaining retries.
3. Confirm that the process creates the expected incident.
4. Confirm that approved recipients receive the notification through the selected channel.
5. Check that the notification contains the useful context approved by the project.
6. Check that the notification does not expose secrets or sensitive business data.
7. Check the selected duplicate-handling policy. Test redelivery when the integration can retry delivery.
8. Record the target version, integration, process, job, incident, expected and actual delivery counts, and redacted evidence in `MIGRATION_REPORT.md`.

## Independent validation results

Keep these results separate in the migration report:

| Validation | Example result |
|---|---|
| Compilation | `passed` or `failed` |
| Worker registration | `passed` or `failed` |
| Incident visibility | `passed` or `failed` |
| Notification parity | `blocked`, `verified`, or `waived` |

Compilation, worker registration, and incident visibility do not prove notification parity.

This fixture defines a migration assessment and test plan. It does not provide an alerting integration or claim that notification delivery was tested.
