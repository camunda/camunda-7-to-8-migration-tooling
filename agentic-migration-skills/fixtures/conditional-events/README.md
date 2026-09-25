# Conditional boundary event fixture

This M2 regression fixture covers a Camunda 7 conditional boundary event whose
nested `bpmn:conditionalEventDefinition` has no `id`. The expected Camunda 8
copy adds a unique definition ID and keeps the original event, sequence-flow,
and BPMN DI IDs.

## Run the M2 evaluation

1. Create a temporary project. Copy only `c7-source/conditional-boundary.bpmn`
   into its root.
2. Run `migrate-c7-to-c8-code` with the temporary project as the confirmed
   project root. Select **Models only**, target **Camunda 8.9**, and **Agentic AI**.
3. Confirm that the skill writes `converted-c8-conditional-boundary.bpmn`.
4. Check the converted copy with Python 3:

   ```sh
   python3 verify_conditional_event_ids.py \
     c7-source/conditional-boundary.bpmn \
     /path/to/project/converted-c8-conditional-boundary.bpmn
   ```

5. Lint the converted copy with the Camunda 8.9 compatibility ruleset. The ID
   check must still run when the linter reports no ID-related finding:

   ```sh
   c8ctl bpmn lint /path/to/project/converted-c8-conditional-boundary.bpmn
   ```

## Runtime check

Run the deployment check against a verified local Camunda 8.9 or later cluster.
Do not use a mock endpoint or a cluster with an unknown version as runtime
evidence.

```sh
c8ctl deploy /path/to/project/converted-c8-conditional-boundary.bpmn --profile=local
c8ctl await pi \
  --id M2ConditionalBoundaryEventIdFixture \
  --variables '{"shouldEscalate":true}' \
  --requestTimeout 60000 \
  --profile=local
```

The interrupting boundary event fires when the service task activates because
`shouldEscalate` is `true`. Use a cluster where no worker handles the fixture's
`orderReviewDelegate` job type. The `await pi` command waits for process
completion. The normal path remains at that unhandled job, so successful
completion confirms that the boundary path fired. Record the cluster version,
deployment result, and execution result in `MIGRATION_REPORT.md`. Keep the
finding **needs review** when this runtime check cannot run.
See the [conditional-event documentation][conditionals] for this activation
behavior.

## Expected checks

| Check | Expected result |
|---|---|
| Source conditional definition | At least one definition has no `id`. |
| Converted conditional definitions | Every definition has a nonempty, unique `id`. |
| Existing BPMN IDs | Event and sequence-flow IDs remain unchanged. |
| BPMN DI | Diagram, shapes, edges, geometry, and references remain unchanged. |
| Runtime | The converted copy deploys and the conditional boundary completes the process. |

`expected-c8/converted-c8-conditional-boundary.bpmn` is a reference conversion
for offline structural checks. The Python verifier also accepts the converted
copy produced by the M2 evaluation.

The Python verifier is specific to this fixture. Apply the skill's Step 5
conditional-event ID checks to every source and converted copy in a migration.
Run the verifier regression tests from the repository root:

```sh
python3 -m unittest discover \
  -s agentic-migration-skills/fixtures/conditional-events \
  -p 'test_*.py'
```

[conditionals]: https://docs.camunda.io/docs/components/concepts/conditionals/#on-scope-activation
