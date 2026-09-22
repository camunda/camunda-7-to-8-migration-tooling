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
5. Run the structural checks:

   ```sh
   python3 verify_di_preservation.py bpmn-di-c7.bpmn converted-c8-bpmn-di-c7.bpmn
   python3 verify_di_preservation.py --no-source-di no-di-c7.bpmn converted-c8-no-di-c7.bpmn
   ```

6. Install the BPMN lint dependencies once, then lint the converted DI copy:

   ```sh
   npm install -D bpmnlint zeebe-bpmn-moddle bpmnlint-plugin-camunda-compat
   npx bpmnlint converted-c8-bpmn-di-c7.bpmn 2>&1 | tee bpmn-di-lint.log
   ! grep -F "no-bpmndi" bpmn-di-lint.log
   ```

   Use the target-compatible Camunda compatibility ruleset described in the
   skill reference. The DI copy must introduce no `no-bpmndi` finding.

7. Lint the no-DI control with the same ruleset. Record any `no-bpmndi`
   finding as inherited source quality only when the converted copy still has no
   DI. Do not add a layout to silence that finding.
8. Record the source and converted DI counts, reference checks, lint output, and
   the no-DI provenance in `MIGRATION_REPORT.md`.

## Expected checks

| Source | Expected converted copy | Expected result |
|---|---|---|
| `bpmn-di-c7.bpmn` | Diagram and DI retained | The script passes. Shape and edge references still target the same IDs. |
| `no-di-c7.bpmn` | No DI added | The script passes. The report records absent source DI and inherited lint findings. |

The original Camunda 7 files must remain byte-for-byte unchanged. A missing,
unmapped, or newly invented DI reference is a failed evaluation.
