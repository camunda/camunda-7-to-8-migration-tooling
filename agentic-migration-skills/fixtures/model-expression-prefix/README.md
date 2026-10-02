# M2 FEEL expression-prefix fixture

This fixture covers M2 conversions that write a dynamic FEEL expression without
the required leading `=`. It also covers leftover BPMN expression-language
attributes. The regression tests use synthetic source and converted XML in a
temporary directory.

The validator checks non-literal values for a leading `=` when the condition
language is missing, JUEL, or FEEL. Other explicit condition languages are
blocking redesign findings, even when a converted condition has a leading `=`.

The validator pairs source and converted elements by BPMN XML ID for dynamic
input/output mappings, subscription keys, task types, call targets, assignments,
and form IDs. It does not treat static values such as
`candidateGroups="approvers"` as expressions.
The validator treats input and output parameters with nested
`camunda:script scriptFormat="feel"` elements as dynamic expressions.
The validator rejects a dynamic input/output mapping when the converted copy has
no target with the source parameter's name.
Every converted `zeebe:subscription/@correlationKey` requires a leading `=`,
including a key that has no matching source subscription.

Run the regression tests from the repository root:

```sh
python3 -m unittest discover \
  -s agentic-migration-skills/fixtures/model-expression-prefix \
  -p 'test_*.py'
```

For an M2 migration, run the validator once for every source and converted pair:

```sh
python3 "<skill-directory>/scripts/validate_model_expressions.py" \
  --pair "<source.bpmn>" "<converted-c8-source.bpmn>"
```

Repeat `--pair` for every in-scope BPMN or DMN model.
