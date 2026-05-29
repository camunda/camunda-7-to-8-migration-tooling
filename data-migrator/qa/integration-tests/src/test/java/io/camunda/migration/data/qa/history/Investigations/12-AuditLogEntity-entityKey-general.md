# Investigation prompt — Row #12: `AuditLogEntity.entityKey` (general)

## Your job

Verify whether row #12 is a real distinct case or a duplicate of #4 (`JOB`)
and #5 (`EXTERNAL_TASK`).

To answer: read the migrator's audit-log dispatch from code, enumerate every
C7 `UserOperationLogEntry` entity type the migrator can encounter, and
determine which entity types land `entityKey` as null in the migrated row.

If unhandled types exist beyond JOB and EXTERNAL_TASK, also produce the four
Phase B items (root cause, data in scope, implementation cost per option,
user-facing impact) for those types — derived from code, not from existing
docs.

Then compare your findings against `NULLABILITY-status.md` row #12. Treat
that doc as evidence to be audited, not briefing to be trusted.

See `_PROMPT-PREAMBLE.md` for methodology, options, principles, and
deliverable shape.
