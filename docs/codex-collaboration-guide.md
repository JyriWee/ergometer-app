# Codex Collaboration Guide

Use this guide when working with Codex in this repository.

## 1) Mark intent in every request

Start each request with one of:

- `DISCUSS` when you want analysis, options, or design discussion only.
- `IMPLEMENT` when you want code changes and validation.

This avoids ambiguity and reduces back-and-forth.

## 2) Always include Definition of Done

For each task, define clear acceptance criteria, for example:

- "Button text is red in both fallback modes."
- "Error beep is played on both mapping-failure paths."
- "Debug compile + unit tests pass."

## 3) Use a consistent bug report format

When reporting an issue, include:

- Reproduction steps
- Expected behavior
- Actual behavior
- Logcat text (plain text preferred over image)

## 4) State Git policy with each integration request

When asking for `commit/push/merge`, specify:

- Target branch
- Merge style (`merge commit` vs `squash`)
- Whether feature branch should be deleted after merge

## 5) Keep session handoff notes in-repo

Maintain `docs/next-session.md` with:

- Current working branch
- Next task to execute
- Known risks/open questions
- Validation commands

## 6) Prefer small, testable increments

Best cadence:

1. Implement one focused change
2. Validate in app/tests
3. Continue to next increment

This minimizes regression risk and makes failures easy to isolate.

---

## Suggested `docs/next-session.md` template

```md
# Next Session

## Branch
- current: `feature/...`

## Next Task
- IMPLEMENT: ...

## Definition of Done
- ...
- ...

## Risks / Open Questions
- ...

## Validation
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --no-daemon`
3. Manual check: ...
```
