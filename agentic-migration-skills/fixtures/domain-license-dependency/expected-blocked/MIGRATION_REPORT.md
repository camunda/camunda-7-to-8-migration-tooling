# Expected blocker report

## Dependency inventory

| Dependency | Uses | Target compatibility | Classification | Decision |
|---|---|---|---|---|
| `org.camunda.bpm:license-generator-fixture` | `LicenseService.generateUnifiedLicenseKey` creates both fixture license types, `TYPE_A` and `TYPE_B`. `LicenseProvisioningService.createLicense` and `LicenseProvisioningService.updateLicense` call it and update directory membership. | Incompatible with the target runtime for this scenario. | Active domain library. The `org.camunda.bpm` group is a review signal, not proof of an engine-only dependency. | No project-owner-approved replacement exists. Keep the dependency and active code unchanged. Mark both call sites as blocking/manual. Do not report either flow as migrated. |

## Open items

| Call site | Question | Status |
|---|---|---|
| `LicenseProvisioningService.createLicense` | Which project-owner-approved library can preserve both license types on the target runtime? | `blocking/manual` |
| `LicenseProvisioningService.updateLicense` | Which project-owner-approved library can preserve both license types on the target runtime? | `blocking/manual` |

These open items block both license flows. Do not replace either call path with an exception or
fabricated license material. Do not report these flows as migrated.
