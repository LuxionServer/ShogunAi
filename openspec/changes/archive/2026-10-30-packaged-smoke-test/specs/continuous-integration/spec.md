# Spec Delta

## MODIFIED Requirements

### Requirement: A release builds only after its tests pass
The release workflow SHALL run the full test suite on the release commit, on every target operating system, before any installer is built or any release is created, whichever event started it. It SHALL then run the packaged smoke test on every installer it built, and SHALL create the tag, the draft release and the attestations, publish the release and update the package manifests only after the smoke test passes on every operating system.

#### Scenario: Release from a pushed tag
- **WHEN** a `vX.Y.Z` tag is pushed and a test fails on that commit
- **THEN** no installer is built and no release is created

#### Scenario: Release from a merged pull request
- **WHEN** the release pull request is merged and every test and every smoke test passes
- **THEN** the installers are built, smoke tested and published as before

#### Scenario: Smoke test fails on one operating system
- **WHEN** the release pull request is merged, every test passes and the smoke test of the `.msi` fails
- **THEN** no tag is created, no release is created or published and no package manifest is updated
