# M1 model lint readiness fixture

`order-c7-no-di-form-free.bpmn` is a Camunda 7 source with valid sequence-flow references, no BPMN
DI, and no `bpmn:incoming` or `bpmn:outgoing` children. Its `ReviewOrder` user task has no form
metadata, and the expected user decision is to keep it form-free.
`converted-c8-order-no-di-form-free.bpmn` preserves these absences.
`converted-c8-order-disconnected.bpmn` adds a disconnected user task that the graph check must reject.

Run the fixture checks from the repository root:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/model-lint-readiness -p 'test_*.py'
```

`verify_bpmn_connectivity.py` supports only these single-process fixtures. It rejects subprocesses,
transactions, and boundary events, and it is not a general BPMN validator.

`c8ctl bpmn lint converted-c8-order-no-di-form-free.bpmn` still reports DI, connectivity, implicit
start and end, label, and missing-form findings. The skill accepts them only as described in
[`model-lint-and-connectivity.md`](../../skills/migrate-c7-to-c8-code/references/model-lint-and-connectivity.md),
after the graph check and a target runtime check that completes `ReviewOrder`.
