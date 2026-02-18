# Release Checklist (`v0.1.0`)

This checklist is the release Definition of Done for the first public version.

## 1. Freeze and Branch
- [ ] Start from clean `main`.
- [ ] Create release prep branch:
  - `git checkout -b feature/v0-1-0-release-prep`
- [ ] Confirm branch protection is active on `main` (required PR + required checks).

## 2. Versioning
- [ ] Update `app/build.gradle.kts`:
  - `versionCode` incremented from previous release.
  - `versionName` matches release tag (for example `0.1.0`).
- [ ] Update `CHANGELOG.md` with final release scope.

## 3. CI and Build Validation
- [ ] Run local validation:
  - `./gradlew :app:compileDebugKotlin --no-daemon`
  - `./gradlew :app:testDebugUnitTest --no-daemon`
  - `./gradlew :app:lintDebug --no-daemon`
- [ ] Run release validation:
  - `./gradlew :app:assembleRelease --no-daemon -Pergometer.release.minify=true`
  - `./gradlew :app:lintRelease --no-daemon -Pergometer.release.minify=true`
- [ ] Ensure GitHub Actions `build-test-lint` is green on release PR.

## 4. Manual Validation (Device)
- [ ] Run core user-path checks in both dark and light themes:
  - MENU
  - SESSION
  - SUMMARY
  - WORKOUT EDITOR
- [ ] Run BLE smoke flow:
  - Trainer discovery -> start session -> end session.
- [ ] Confirm no clipping with system bars and no blocked CTA states.
- [ ] Store key screenshots for release note evidence.

## 5. Artifact Mode Decision

Choose one mode and complete only that path.

### Mode A: Unsigned release artifact (fast baseline)
- [ ] Build unsigned release APK:
  - `./gradlew :app:assembleRelease --no-daemon -Pergometer.release.minify=true`
- [ ] Verify output exists:
  - `app/build/outputs/apk/release/app-release-unsigned.apk`

### Mode B: Signed release artifact (recommended)
- [ ] Configure GitHub secrets:
  - `ANDROID_RELEASE_KEYSTORE_B64`
  - `ANDROID_RELEASE_STORE_PASSWORD`
  - `ANDROID_RELEASE_KEY_ALIAS`
  - `ANDROID_RELEASE_KEY_PASSWORD`
- [ ] Verify signed release build path in CI.
- [ ] Verify local signed build (optional but recommended) using environment variables documented in `docs/ci-release-signing.md`.

## 6. Publish
- [ ] Merge release prep PR to `main`.
- [ ] Create tag:
  - `git tag -a v0.1.0 -m "ErgometerApp v0.1.0"`
  - `git push origin v0.1.0`
- [ ] Create GitHub Release from tag `v0.1.0`.
- [ ] Paste release notes draft from `docs/release-notes-v0.1.0.md`.
- [ ] Attach release artifact (signed or unsigned according to selected mode).

## 7. Post-Release
- [ ] Confirm release page is public and downloadable.
- [ ] Open next milestone issue for `v0.2.0`.
- [ ] Update `docs/next-session.md` with post-release follow-up tasks.

