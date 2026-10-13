# M1 model lint readiness fixture

The Camunda 7 source has valid sequence-flow references and no BPMN DI. It has no
`bpmn:incoming` or `bpmn:outgoing` children. Its `ReviewOrder` user task has no form metadata. The
converted copy preserves these absences. The expected user decision is `keep form-free`. A second
converted copy has a disconnected user task. The connectivity check must reject it.

Run the namespace-aware fixture checks from the repository root:

```sh
python3 -m unittest discover \
  -s agentic-migration-skills/fixtures/model-lint-readiness \
  -p 'test_*.py'
```

The verifier checks the BPMN namespace, flow endpoints, optional incoming/outgoing references, and
explicit start-to-end reachability. It supports only the simple processes in these fixtures. It
fails closed for subprocesses, boundary events, and transactions. Use BPMN semantics for those
constructs in a migration run. Do not use this fixture verifier as a general BPMN validator.

The test suite verifies that a user task with Camunda form metadata is not eligible for the
form-free lint exception. It does not run `c8ctl bpmn lint` or contact a Camunda cluster.
`../../skills/migrate-c7-to-c8-code/references/model-lint-and-connectivity.md` defines the M1 lint,
exception, and runtime gate.

The raw target lint remains visible. On c8ctl 4.2.0, this command exits with 12 errors and one
warning. It reports missing BPMN DI, disconnection, implicit starts and ends, and a missing-form
warning. The connected model still passes the namespace-aware graph check.

```sh
c8ctl bpmn lint \
  agentic-migration-skills/fixtures/model-lint-readiness/converted-c8-order-no-di-form-free.bpmn
```

Do not disable those rules in a project-wide `.bpmnlintrc`.

Before marking an actual migration ready, deploy the exact converted copy to the selected target
version. Start `orderProcess`. Confirm that the instance reaches `ReviewOrder`. Complete the user task through the target API or the application's real completion path. Confirm
that the instance completes. Record this runtime evidence as described in
`../../skills/migrate-c7-to-c8-code/references/model-lint-and-connectivity.md`.
