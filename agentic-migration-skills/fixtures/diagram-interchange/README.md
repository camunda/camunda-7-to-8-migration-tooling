# BPMN DI preservation fixture

This directory evaluates the agentic model-rewrite path of the
`migrate-c7-to-c8-code` skill. It covers a Camunda 7 source model with complete
BPMN diagram interchange (DI) and a control model without DI.

```text
bpmn-di-c7.bpmn
no-di-c7.bpmn
verify_di_preservation.py
```

The source with DI contains one diagram, one plane, three shapes, three labels,
and two edges. Every shape has bounds. Every edge has two waypoints. The model
uses a service task so the rewrite changes execution semantics while keeping the
semantic IDs stable.

The control source contains the same semantic flow without a BPMN DI tree. The
skill must not invent a layout for that source.

## Running the evaluation

1. Copy this directory into a temporary project and keep both originals unchanged.
2. Run the migration skill with **Models only**, target **Camunda 8.9**, and
   **Agentic AI**.
3. Require the skill to parse and inventory source DI before rewriting.
4. Require the skill to write converted copies named
   `converted-c8-bpmn-di-c7.bpmn` and `converted-c8-no-di-c7.bpmn`.
5. Run the structural checks with Python 3.

   On macOS or Linux, run:

   ```sh
   python3 verify_di_preservation.py bpmn-di-c7.bpmn converted-c8-bpmn-di-c7.bpmn
   python3 verify_di_preservation.py --no-source-di --report MIGRATION_REPORT.md no-di-c7.bpmn converted-c8-no-di-c7.bpmn
   ```

   On Windows PowerShell, run:

   ```powershell
   py -3 verify_di_preservation.py bpmn-di-c7.bpmn converted-c8-bpmn-di-c7.bpmn
   py -3 verify_di_preservation.py --no-source-di --report MIGRATION_REPORT.md no-di-c7.bpmn converted-c8-no-di-c7.bpmn
   ```

6. Install the BPMN lint dependencies once.
   Run `npm install -D bpmnlint zeebe-bpmn-moddle bpmnlint-plugin-camunda-compat`.
7. Create a `.bpmnlintrc` file with this JSON content:

   ```json
   {
     "extends": [
       "bpmnlint:recommended",
       "plugin:camunda-compat/camunda-cloud-8-9"
     ],
     "moddleExtensions": {
       "zeebe": "zeebe-bpmn-moddle/resources/zeebe.json"
     }
   }
   ```

8. Capture the DI-copy lint output in `bpmn-di-lint.log` and keep the
   `bpmnlint` exit code.

   On macOS or Linux, run:

   ```sh
   set -o pipefail
   npx bpmnlint converted-c8-bpmn-di-c7.bpmn 2>&1 | tee bpmn-di-lint.log
   ```

   On Windows PowerShell, run:

   ```powershell
   npx bpmnlint converted-c8-bpmn-di-c7.bpmn 2>&1 | Tee-Object -FilePath bpmn-di-lint.log
   if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
   ```

9. Confirm that `bpmn-di-lint.log` contains no `no-bpmndi` finding.

   Use the target-compatible Camunda compatibility ruleset described in the
   skill reference. The DI copy must introduce no `no-bpmndi` finding.

10. Lint the no-DI control with the same ruleset. Use the same command pattern
   to capture output and keep the exit code. Record any `no-bpmndi`
   finding as inherited source quality only when the converted copy still has no
   DI. Do not add a layout to silence that finding.
11. Record the source and converted DI counts, reference checks, complete
   per-element DI comparisons, namespace bindings, lint output, and the no-DI
   provenance in `MIGRATION_REPORT.md`. Include the control filename and an
   explicit statement that its source BPMN DI is absent.

## Expected checks

| Source | Expected converted copy | Expected result |
|---|---|---|
| `bpmn-di-c7.bpmn` | Diagram and DI retained | The script passes. DI attributes, child geometry, namespace bindings, and references remain unchanged. |
| `no-di-c7.bpmn` | No DI added | The script passes with `MIGRATION_REPORT.md`. The report records absent source DI and inherited lint findings. |

The original Camunda 7 files must remain byte-for-byte unchanged. A missing,
unmapped, or newly invented DI reference is a failed evaluation.
