## Codex usage contract (ergometer-app)

Codex is allowed to:
- Modify UI code (Compose, layout, theme, strings).
- Reorganize composables and visual structure.
- Expose already existing data to the UI.

Codex must NOT:
- Add new UI state (remember, mutableStateOf) that is not derived from RunnerState.
- Move logic from WorkoutRunner, ExecutionWorkout, FTMS, or parsers into UI.
- Interpret, prioritize, or reclassify domain data (e.g. "primary metrics").
- Change control semantics (STOP, PAUSE, ERG ownership).

Rules of operation:
- Prefer unified diff output.
- One logical change per request.
- No refactors unless explicitly requested.
