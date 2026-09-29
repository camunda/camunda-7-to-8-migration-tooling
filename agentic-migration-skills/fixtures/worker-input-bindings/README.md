# Worker input bindings

This fixture covers three single-variable inputs and a complete process-variable map.
The Camunda 7 source is under `c7-source`.
The executable Camunda 8.9.21 project is under `expected-c8`.

The Maven compiler disables parameter-name metadata.
Runtime tests check each input binding and the no-parameter-name case.
The map worker receives two differently named variables.
The process does not define a variable named `variables`.

Run the tests from the repository root:

```sh
mvn -f agentic-migration-skills/fixtures/worker-input-bindings/expected-c8/pom.xml test
```

The tests use Camunda Process Test and Docker.
They verify that each source input reaches its worker.
They verify that missing required inputs create incidents.
They verify that the optional Twitter content input remains null when it is absent.
