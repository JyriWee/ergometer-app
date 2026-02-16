# Next Session

## Branch
- current: `work`

## Recently Completed
- Completed a full technical audit of the current main-latest snapshot, focused on FTMS/BLE reconnect behavior, command ownership/race risks, workout pipeline resilience, UI state transitions, and CI/release guardrails.
- Produced prioritized remediation roadmap (P0/P1/P2), quick wins (<1 day), and targeted validation matrix for reconnect + portrait/landscape behavior.
- Documented concrete regression risks and proposed fixes in `docs/technical-audit-main-latest.md`.

## Next Task
- Implement P0 hardening for FTMS request-control failure/timeout handling and command-response correlation invariants.

## Definition of Done
- Request-control non-success and timeout paths always resolve to deterministic UI state with actionable recovery.
- FTMS command pipeline validates response opcode against expected in-flight command.
- Unit tests cover denied-control, timeout, and mismatched-response scenarios.
- No regressions in existing workout start/stop flow.

## Risks / Open Questions
- Product decision needed: strict vs degraded execution mode default for unsupported workout steps.
- Product decision needed: reconnect retry budget and user messaging policy.
- Potential scope creep if HR reconnect hardening is bundled into same sprint.

## Validation
1. `./gradlew :app:testDebugUnitTest --no-daemon`
2. `./gradlew :app:lintDebug --no-daemon`
3. `./gradlew :app:assembleRelease --no-daemon -Pergometer.release.minify=true`
4. Manual: trainer power-cycle reconnect test (during session and during stopping flow).
5. Manual: rotate portrait/landscape in MENU, CONNECTING, SESSION, STOPPING, SUMMARY.
