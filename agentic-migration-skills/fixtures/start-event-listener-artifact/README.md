# Start-event listener artifact fixture

This fixture checks the selected Diagram Converter CLI artifact against #2825.
It includes four Camunda 7 start listeners on `Start_Listener` and a no-listener control.

## Check the selected artifact

From this directory, pass the exact Java executable, CLI JAR, and target
version that the migration run selected:

```sh
python3 -m unittest -v test_verify_cli_artifact.py
python3 verify_cli_artifact.py \
  --java /path/to/validated-jdk/bin/java \
  --jar /path/to/camunda-7-to-8-diagram-converter-cli-<tag>.jar \
  --target-version 8.9
```

Use the Java executable, JAR, and target version selected by the migration run.
The probe runs both models from one directory. It requires a distinct, blocking
`TASK` finding for each source implementation and none for the control.
It also rejects a converted copy with a `start` listener on any start event.
Release 0.3.8 predates the fix in #2841 and fails this check.

## Check the migration workflow

1. Run the migration skill on `c7-source` with target version 8.9 and M1 selected.
2. Confirm `MIGRATION_REPORT.md` records the selected JAR, Java executable,
   target version, and source listener at `Start_Listener`.
3. If the CLI omits or downgrades a finding, record a source-derived blocking
   finding. Do not count it as proof that the CLI contains the fix.
4. Accept or decline the listener relocation when the skill asks. Do not treat a
   matching worker as evidence that the original placement deploys.
5. Verify the authorized target and its version. Deploy both converted copies
   after follow-up and record a result for each model.

If the selected artifact fails, retry with a patched release on clean copies of
all original models. Use only the replacement run's reports and converted copies.
Where no patched release exists, request approval for a manual follow-up.
Keep readiness blocked until the follow-up and target deployment pass.
