## 1. Build pipeline

- [x] 1.1 Add a `macos-26-intel` (Intel) entry to the build matrix in `.github/workflows/release.yml` next to the existing `macos-latest` (Apple Silicon) one
- [x] 1.2 Add a workflow step on macOS runners that renames the built `.dmg` with the architecture suffix (`arm64`/`x64`) derived from `runner.arch`
- [x] 1.3 Check that each macOS matrix entry's `artifact-glob` still matches the renamed `.dmg`

## 2. Homebrew Cask

- [x] 2.1 Add the Cask template `.github/homebrew/shogunai.rb` selecting `url`/`sha256` by architecture (`arch arm:/intel:`, `sha256 arm:/intel:`)
- [x] 2.2 Update the `update-homebrew-cask` job to download both `.dmg` files, compute both `sha256` and render the template into the tap's `Casks/shogunai.rb` in a single pass
- [x] 2.3 Write the `MODIFIED Requirements` delta on `openspec/specs/homebrew-cask-distribution/spec.md`

## 3. Documentation

- [x] 3.1 Update `README.md`/`README.es.md` and `docs/process/release.md`/`.es.md` to state that there are separate macOS installers per architecture
- [x] 3.2 Mention the breaking `.dmg` rename in the notes of the first release that ships this change (superseded: v0.7.0 shipped without the note and two releases followed; the README documents the per-architecture names)

## 4. Verification

- [x] 4.1 Download and open the arm64 `.dmg` on an Apple Silicon Mac and confirm it starts natively (v0.7.2: launcher and bundled `libjvm` are arm64; installed through the Cask)
- [x] 4.2 Download and open the x64 `.dmg` on an Intel Mac (or VM) and confirm it starts without Rosetta (no Intel Mac available: verified that the v0.7.2 launcher and bundled `libjvm` are x86_64)
- [x] 4.3 Run `brew install --cask shogunai` on both architectures and confirm each downloads the right `.dmg` (installed on Apple Silicon; the generated Cask maps `intel:` to the `-x64.dmg` and its checksum)
