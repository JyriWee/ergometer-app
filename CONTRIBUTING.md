# Contributing

Thanks for contributing to ErgometerApp.

## Workflow
1. Create a feature branch from `main`.
2. Keep changes focused and small.
3. Run validation commands locally.
4. Open a pull request to `main`.

## Validation
Run from repository root:

```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
```

## Code and Documentation Style
- Keep behavior deterministic in BLE/session critical paths.
- Prefer clear, testable changes over large refactors.
- Use English for all comments and documentation.
- Use KDoc for public classes/functions when behavior or invariants are non-obvious.

## Pull Request Checklist
- [ ] Scope is limited to one clear task.
- [ ] Validation commands pass locally.
- [ ] UI changes include screenshots (dark and light themes when relevant).
- [ ] `docs/next-session.md` is updated.

