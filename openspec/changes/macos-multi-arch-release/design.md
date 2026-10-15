## Context

`compose.desktop` (jpackage) produces a self-contained `.dmg` bundling the JDK runtime of the runner where the build runs; it does not produce universal (`arm64` + `x86_64`) packages without extra steps. The Homebrew Cask (`homebrew-cask-distribution`) is already published in the `LuxionServer/homebrew-shogunai` tap and archived as a spec, so this change modifies it too.

GitHub-hosted runner availability (checked 2026-10-16 against the [GitHub-hosted runners reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)): standard macOS runners for public repositories are arm64 (`macos-latest`, `macos-14`, `macos-15`, `macos-26`) and Intel (`macos-15-intel`, `macos-26-intel`). `macos-13`, which this change originally assumed, was retired on 2025-12-04 ([changelog](https://github.blog/changelog/2025-09-19-github-actions-macos-13-runner-image-is-closing-down/)). GitHub has announced that Intel macOS images end with Apple's x86_64 support (around fall 2027).

## Goals / Non-Goals

**Goals:**
- Publish in every release a `.dmg` that starts natively on Apple Silicon Macs and another one that starts natively on Intel Macs.
- Predictable asset names with no collision between architectures.
- `brew install --cask shogunai` installs the right `.dmg` for the user's architecture automatically.

**Non-Goals:**
- A "universal" (fat) `.dmg` — jpackage does not support it natively for the JVM; two separate artifacts are preferred over third-party tooling.
- Signing/notarizing the installers — still out of scope, as in `homebrew-cask-distribution`.
- More architectures outside macOS (Windows/Linux stay single-arch).

## Decisions

- **Two `.dmg` files per release, not a universal binary**: jpackage packages the runner's JDK as is; a universal build would need a universal JDK and manual `lipo` steps the Compose plugin doesn't support. Two artifacts is what the current toolchain supports without new dependencies.
- **Intel runner: `macos-26-intel`**: the newest Intel image GitHub offers to public repositories. `macos-15-intel` would also work but, under GitHub's N-1 image policy, it will be retired before `macos-26-intel`. The label is pinned (no `-latest` alias exists for Intel). When GitHub retires Intel macOS runners altogether, the Intel entry has to be dropped or moved to a paid/self-hosted runner; there is no automatic mitigation.
- **arm64 runner stays `macos-latest`**: the alias is arm64 and will stay so (Intel is being phased out), so pinning it buys nothing.
- **Architecture suffix added by a post-build rename in the workflow, derived from `runner.arch`**: Compose's `nativeDistributions` has no option to add a suffix to the `.dmg` name, and changing `packageName` would also rename the `.app`. A step on macOS runners renames `ShogunAi-<v>.dmg` to `ShogunAi-<v>-<arch>.dmg`, where `<arch>` is the lowercased `RUNNER_ARCH` (`ARM64` → `arm64`, `X64` → `x64`). Deriving it from the runner itself means a mislabeled matrix entry can never publish an Intel build under the `arm64` name or vice versa. The existing `artifact-glob` (`dmg/*.dmg`) keeps matching after the rename.
- **No unsuffixed alias asset**: the Cask is the main macOS channel and resolves the right file transparently; manual downloaders get both files side by side with explicit names. Accepted as a breaking rename, called out in the README and in the notes of the first release that ships it.
- **Cask generated from a template in this repository, not patched in place**: `.github/homebrew/shogunai.rb` holds the full Cask with `@VERSION@`, `@SHA256_ARM@` and `@SHA256_INTEL@` placeholders; `update-homebrew-cask` checks out the repo, downloads both `.dmg` files, computes their `sha256`, renders the template and overwrites `Casks/shogunai.rb` in the tap. Patching with `sed` (the previous approach) cannot turn a single-arch Cask into a multi-arch one, and would require a manual, unversioned edit in the tap. With the template, the Cask source is reviewed in PRs here, and re-running the job for an already-published version still produces no diff (no commit).
- **Architecture selection with the `arch` stanza plus `sha256 arm:/intel:`**: this is the Cask DSL's documented shorthand for the `on_arm`/`on_intel` blocks when only the URL and checksum differ per architecture; Homebrew resolves it with `Hardware::CPU.arch` exactly like the blocks. `arch arm: "arm64", intel: "x64"` maps directly onto the asset suffixes. The `url` keeps `#{version}` (and the `1.<minor>.<patch>` `dmg_version` derivation from `homebrew-cask-distribution`) so `brew audit` doesn't flag an unversioned URL.

## Risks / Trade-offs

- [macOS CI minutes double] → Accepted: releases are infrequent.
- [Renaming the asset breaks existing links/scripts pointing at the unsuffixed `.dmg`] → Accepted (see decisions); `releases/latest` links to the release page, not to a file, keep working.
- [`macos-26-intel` gets retired, or Intel macOS runners disappear (announced for ~fall 2027)] → Manual maintenance: drop the Intel entry or switch to a paid/self-hosted Intel runner, and update the Cask template if Intel is no longer published.
- [The tap's Cask has been edited by hand] → The generated Cask overwrites it; any manual change in the tap must be moved into the template here.
- [One macOS build fails while the other succeeds (`fail-fast: false`)] → `publish-release` `needs: build`, so any failed matrix entry leaves the release in draft, and `update-homebrew-cask` uses `curl -f`, failing visibly if either `.dmg` is missing instead of writing a checksum of an error page.

## Open Questions

- Whether to evaluate a universal build with third-party tooling in the future to go back to a single `.dmg`, given the doubled CI cost. Moot once Intel runners are retired.
