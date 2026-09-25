# Expected blocker report

## Open items

| Call site | Question | Status |
|---|---|---|
| `LicenseProvisioningService.createLicense` | Which project-owner-approved library can preserve both license types on the target runtime? | `open` |
| `LicenseProvisioningService.updateLicense` | Which project-owner-approved library can preserve both license types on the target runtime? | `open` |

This item blocks both license flows. Do not replace either call path with an exception or fabricated
license material. Do not report these flows as migrated.
