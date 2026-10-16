# CPT runtime preflight

This fixture checks two test-migration decisions. An explicit early test choice applies only when
the Step 2 inventory confirms eligible tests. A Docker-dependent CPT suite must run one mapped test
method before the full repeat run.

The runtime tests use synthetic JUnit results. They reject a skipped test, a class initialization
error, and a method from another suite. They classify a Java Docker-client API mismatch as
compatibility rather than an unavailable Docker daemon. A passing Docker CLI check cannot unlock
the repeat run when the CPT runtime preflight failed.
The fixture also checks suite-specific baseline membership, added-only suites, retry after a
zero-exit failure, and evidence retained after the last CPT test is retired.

Run the focused checks from the repository root:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/cpt-runtime-preflight
```

The fixture does not launch Docker. The validator's `cpt_runtime_preflight` check runs the actual
selected CPT method in a migrated project.
