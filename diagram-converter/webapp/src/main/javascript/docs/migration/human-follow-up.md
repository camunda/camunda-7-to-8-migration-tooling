
## Bulk run 2026-07-08 — FLAG items

### src/App.jsx — ProgressIndicator / ProgressStep

- **Symbols:** `ProgressIndicator`, `ProgressStep` (imported from `@carbon/react`)
- **Why flagged:** No `carbon-compat` adapter; not in `mapping.json`. DS has `Stepper`/`StepperStep` but with a different API and visual shape — requires design input.
- **Impact:** `@carbon/react/index.scss` must remain in `src/main.jsx` until this is resolved (Carbon CSS required for correct styling).
- **Action required:** Evaluate DS `Stepper`/`StepperStep` as the migration target. If the visual shape is acceptable, remap; otherwise raise with Design System team.
