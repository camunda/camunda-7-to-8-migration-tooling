# Custom Incident Notifications

This fixture checks that the migration flow keeps custom incident notifications separate from job-worker migration.

## Source behavior

The Camunda 7 default handler stores failed-job incidents. The custom handler adds a synchronous email notification.

| Source file | Behavior |
|---|---|
| `pom.xml` | Declares the Camunda 7 engine and JavaMail dependencies. |
| `src/main/java/org/camunda/bpm/example/incident/EngineConfigurationFactory.java` | Adds the incident handler plugin to the process engine configuration. |
| `src/main/java/org/camunda/bpm/example/incident/IncidentHandlerProcessEnginePlugin.java` | Enables composite handlers and registers the custom `failedJob` handler. |
| `src/main/java/org/camunda/bpm/example/incident/ErrorNotificationIncidentHandler.java` | Sends an email for each failed-job handler invocation. |
| `src/main/java/org/camunda/bpm/example/incident/EmailNotificationClient.java` | Sends SMTP email with the failure message and incident context. |
| `src/main/resources/incident-notifications.properties` | Defines the SMTP settings and recipient addresses. |

The email includes the process definition, execution, activity, job definition, and failure message.
The handler does not deduplicate notifications or retry failed deliveries.
Compile the sample with `mvn test` from this directory. The command does not start the engine or send email.

## Expected assessment

The migration report must list a separate incident-notification finding. It must record the handler registration, trigger, recipient configuration, useful message context, duplicate-handling behavior, and data exposed by the notification.

The migration flow must ask the project owner to choose one path:

| Project decision | Expected result |
|---|---|
| Implement a Camunda 8-compatible integration | Allow handler replacement after approval. Keep the finding `blocked` until disposable-target verification passes. |
| Waive notification behavior | Record the approver, date, reason, accepted behavior loss, and decision reference. Resolve the finding as `waived`, not `verified`. |
| No decision recorded | Mark the finding `blocked`, add an open report item, and stop before Step 3 confirmation or deployment. |

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

This fixture provides a Camunda 7 sample handler and SMTP notification client. It does not provide a Camunda 8 integration or claim that notification delivery was tested.
