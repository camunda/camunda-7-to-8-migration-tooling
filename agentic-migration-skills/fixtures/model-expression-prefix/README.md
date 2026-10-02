# M2 FEEL expression-prefix fixture

This fixture covers M2 conversions that write a dynamic FEEL expression without
the required leading `=`. It also covers leftover BPMN expression-language
attributes. The regression tests use synthetic source and converted XML in a
temporary directory.

The validator pairs source and converted elements by BPMN XML ID for dynamic
input/output mappings, subscription keys, task types, call targets, assignments,
and form IDs. It does not treat static values such as
`candidateGroups="approvers"` as expressions.

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
