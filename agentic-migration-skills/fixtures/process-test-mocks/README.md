# Process-test mock migration

This fixture checks that the skill preserves each Camunda 7 mock boundary when it migrates a
process test to Camunda Process Test (CPT).

`c7-source` uses the Camunda 7 engine, `MockExpressionManager`, and `c7-mockito`. Its real-DMN test
uses the shared engine configuration but calls no mock API. `expected-c8` keeps the collaborator
mock, mocks the converted job types, executes the converted DMN, and uses `mockChildProcess` for the
called process. Its Spring test disables every real worker whose job type it mocks.

## Evaluate the fixture

1. Copy `c7-source` to a temporary project and run the migration skill on its mock-containing process tests.
2. Compare the migrated tests and build with `expected-c8`.
3. Run the fixture checks from the repository root:

   ```sh
   python3 agentic-migration-skills/fixtures/process-test-mocks/test_migration_mocks.py
   ```

4. Run the Camunda 7 source tests:

   ```sh
   mvn -f agentic-migration-skills/fixtures/process-test-mocks/c7-source/pom.xml test
   ```

5. Run the CPT tests with Docker available:

   ```sh
   mvn -f agentic-migration-skills/fixtures/process-test-mocks/expected-c8/pom.xml test
   ```

The C7 and CPT process tests execute the deployed DMN table and assert the same two-field result
map. The CPT tests also verify the collaborator call, mocked delegate outputs, execution-listener
and user-task-listener job types, task-listener invocation counts, called-process output, BPMN-error
route, and active incident.
CPT tests need Docker.
The unit test verifies that the real `notify-invoice` worker returns the delegate's
`notified=true` output.

## Negative case

`negative/unapproved-worker-mock.json` replaces the real `validate-invoice` worker with a
`mockJobWorker` without a C7 mock or approval. The mock-boundary check must reject this mapping as
an unapproved mock. The Python fixture check calls the parity validator's mock-boundary function.
Run this check after the parity validator is available.
