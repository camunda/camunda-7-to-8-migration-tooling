# Call-activity output scope fixture

This M2 regression fixture covers C7 calls with no output mappings, selected outputs, and all outputs.
Its expected C8 copy disables all-child propagation for no-output and selected-output calls.
The selected-output call keeps its explicit `zeebe:output` mapping.
The all-output call enables all-child propagation.

The verifier checks the XML contract with a namespace-aware parser.
It rejects delegated C7 variable mappings because this static fixture cannot establish their scope.
If the migration cannot establish a compatible C8 mapping, then keep those calls **needs review**.

## Run the M2 evaluation

1. Create a temporary project and copy `c7-source/call-activity-output-scope.bpmn` into its root.
2. Run `migrate-c7-to-c8-code` with that project as the confirmed root.
3. Select **Models only**, target **Camunda 8.9**, and **Agentic AI**.
4. Confirm that the skill writes `converted-c8-call-activity-output-scope.bpmn`.
5. Check the converted copy with Python 3:

   ```sh
   python3 agentic-migration-skills/fixtures/call-activity-output-scope/verify_call_activity_output_scope.py \
     agentic-migration-skills/fixtures/call-activity-output-scope/c7-source/call-activity-output-scope.bpmn \
     /path/to/project/converted-c8-call-activity-output-scope.bpmn
   ```

Run the fixture regression tests from the repository root:

```sh
python3 -m unittest discover \
  -s agentic-migration-skills/fixtures/call-activity-output-scope \
  -p 'test_*.py'
```

The verifier checks the mapping structure only.
It does not replace an authorized deployment and runtime test.
