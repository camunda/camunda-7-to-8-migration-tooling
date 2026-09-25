# Expected blocker report

## Dependency inventory

| Dependency | Uses | Target compatibility | Classification | Decision |
|---|---|---|---|---|
| `org.camunda.bpm:license-generator-fixture` | `LicenseService.generateUnifiedLicenseKey` creates both fixture license types, `TYPE_A` and `TYPE_B`. `LicenseProvisioningService.createLicense` and `LicenseProvisioningService.updateLicense` call it and update directory membership. | Incompatible with the target runtime for this scenario. | Active domain library. The `org.camunda.bpm` group is a review signal, not proof of an engine-only dependency. | No project-owner-approved replacement exists. Keep the dependency and active code unchanged. Mark both call sites as `blocked` with manual follow-up. Do not report either flow as migrated. |

## Open items

| Call site | Manual follow-up | Status |
|---|---|---|
| `LicenseProvisioningService.createLicense` | The project owner must approve a compatible replacement that preserves both license types and directory membership. | `blocked` |
| `LicenseProvisioningService.updateLicense` | The project owner must approve a compatible replacement that preserves both license types and directory membership. | `blocked` |

These blocked open items keep both license flows incomplete. Do not replace either call path with
an exception or fabricated license material. Do not report these flows as migrated.
