# Deployment validation fixture

This fixture covers a model-only migration with one BPMN source and no DMN
source. It reproduces the deployment-list bug from #2501: the migration must
not create a DMN deployment pattern when the converted-copy inventory contains
no DMN file.

## Contents

- `deployment-validation-c7.bpmn` is the only source model.
- `expected-deployment-inventory.tsv` records the one converted BPMN and no
  DMN entry.
- `expected-deployment-patterns.txt` contains the only valid deployment path.
- `check-pattern-coverage.sh` verifies the inventory and pattern coverage.

## Running the regression check

Run the check from the fixture directory:

```sh
cd deployment-validation
sh check-pattern-coverage.sh
```

Expected output:

```text
deployment pattern coverage passed
```

The check fails if a DMN entry or a `.dmn` deployment pattern is added. It
also fails if the recorded BPMN pattern does not match the inventory.

## Skill evaluation

1. Copy `deployment-validation-c7.bpmn` into a temporary Camunda 7 project.
2. Run the migration skill with **Models only** and a target Camunda 8 version.
3. Record only the converted BPMN path in the model deployment inventory.
4. Derive deployment patterns from the recorded inventory and the effective
   build resource mapping.
5. Run `check-pattern-coverage.sh` from this fixture directory.
6. Confirm that the final `@Deployment` or explicit `CamundaClient` command
   contains the recorded BPMN resource and no DMN resource.

The fixture does not require a Maven or Gradle build. The source BPMN must
remain unchanged.
