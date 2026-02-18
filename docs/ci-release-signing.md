# CI Release and Signing

This project verifies both debug and release pipelines in GitHub Actions.

## CI release verification

The workflow runs:

- `:app:assembleRelease` with minification enabled
- `:app:lintRelease`

This catches release-only regressions (R8/proguard/resource shrink/lint) before merge.

## Release signing inputs

Release signing is intentionally externalized and never committed to the repository.

Provide these values through environment variables (preferred for CI) or Gradle properties:

- `ERGOMETER_RELEASE_STORE_FILE` or `ergometer.signing.storeFile`
- `ERGOMETER_RELEASE_STORE_PASSWORD` or `ergometer.signing.storePassword`
- `ERGOMETER_RELEASE_KEY_ALIAS` or `ergometer.signing.keyAlias`
- `ERGOMETER_RELEASE_KEY_PASSWORD` or `ergometer.signing.keyPassword`

The build fails fast if only part of the signing configuration is provided.

If none of the signing inputs are provided, release verification still runs unsigned.

## Practical note for generated keystores

If a keystore is generated with a PKCS12 default setup, use the same value for:

- `ERGOMETER_RELEASE_STORE_PASSWORD`
- `ERGOMETER_RELEASE_KEY_PASSWORD`

Using different values can cause packaging failures when Gradle reads the key from the keystore.

## GitHub Actions secrets

Recommended setup for CI:

1. Store keystore as base64 secret (for example `ANDROID_RELEASE_KEYSTORE_B64`).
2. Decode it in workflow to a temporary file.
3. Export signing variables into the job environment.

Example mapping:

- `ERGOMETER_RELEASE_STORE_FILE`: path to decoded keystore file
- `ERGOMETER_RELEASE_STORE_PASSWORD`: secret value
- `ERGOMETER_RELEASE_KEY_ALIAS`: secret value
- `ERGOMETER_RELEASE_KEY_PASSWORD`: secret value
