## Codex behavior notes

Observation (2026-02):
- Repo-connected Codex (5.3) respects architectural boundaries significantly better than local Codex.
- Local Codex tends to reinterpret UI semantics (e.g. prioritizing metrics, splitting control logic).
- For this project:
  - Use repo-connected Codex for Session / Workout UI.
  - Use local Codex only for isolated composables or layout sketches.
