# Configuration binding fixture

This fixture covers target Camunda Spring Boot starter binding without a cluster.

## Invalid configuration

Validate `application-invalid.yaml`, `application-invalid.yml`, and
`application-invalid.properties` against the selected starter metadata and binding classes.

| Input | Expected finding |
|---|---|
| `camunda.client.mode=simple` | Invalid `ClientMode` value |
| `camunda.client.auth.simple.*` | Unknown nested configuration shape |

The validation must fail before any network connection attempt.

## Valid local configuration

Validate `application-valid.yaml`, `application-valid.yml`, and `application-valid.properties`
against the same starter. The configuration must pass without authentication credentials because it
targets an unauthenticated local self-managed cluster.

The validation must not add Basic or OIDC credentials. If authentication values are supplied, record
only redacted values in `MIGRATION_REPORT.md` and leave the source unchanged.

## Path coverage

Run the fixture after the deterministic configuration recipe. Run it again after an AI-only
migration. Both paths must report the same invalid findings and accept the valid configuration.

Record the selected starter, version, metadata source, binding command, redacted effective
properties, and findings in `MIGRATION_REPORT.md`. Do not report the check as passed when the starter
or its metadata is unavailable.
