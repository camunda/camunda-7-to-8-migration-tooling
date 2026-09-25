# Start-event listener artifact fixture

This fixture checks the selected Diagram Converter CLI artifact and the
`migrate-c7-to-c8-code` workflow against the start-listener regression from #2825.
It includes two Camunda 7 start-event listeners on `Start_Listener` and one
no-listener control.

## Check the selected artifact

From this directory, pass the same CLI JAR and target version that the migration run selected:

```sh
python3 -m unittest -v test_verify_cli_artifact.py
python3 verify_cli_artifact.py \
  --jar /path/to/camunda-7-to-8-diagram-converter-cli-<tag>.jar \
  --target-version 8.9
```

The script runs both fixture models in fresh temporary directories. It requires
one blocking `TASK` `execution-listener-on-start-event` finding for each source
listener. Each finding must match the exact input filename, `Start_Listener`
event, implementation attribute, and implementation value. The converted copy
must omit a `start` execution listener directly on a BPMN start event. The
control model must not report that finding for any filename. A release that
predates the fix in #2841 fails this check.

## Check the migration workflow

1. Run the migration skill on `c7-source` with target version 8.9 and M1 selected.
2. Confirm `MIGRATION_REPORT.md` records the selected release tag and the source
   listener at `Start_Listener`.
3. Confirm the report contains a blocking `execution-listener-on-start-event`
   row, even when the selected artifact omits that finding.
4. Accept or decline the listener relocation when the skill asks. Do not treat a
   matching worker as evidence that the original placement deploys.
5. Deploy both converted copies to an authorized Camunda 8.9 test target after
   follow-up. Record a result for each model.

If the artifact check fails, re-resolve the latest release and rerun conversion
on a clean copy of the original inputs. Use only the replacement run's reports
and converted copies when both checks pass. Archive the failed run's converted
copies outside packaged directories before promoting replacements. Use an
earlier artifact only after the user approves the manual follow-up. If the user
declines relocation, no artifact passes, a destination conflicts, or deployment
cannot run, keep model readiness blocked.
