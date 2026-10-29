# Design

## Context

See proposal.md. The CI smoke test planned by the same roadmap item (`feature/packaged-smoke-test`) installs the `.dmg` on a clean runner automatically; the rehearsal is its human counterpart, on a real Mac and with the guided tour, which a runner can't judge (what the user sees, whether a message makes sense, whether the tour's agent starts).

## Goals / Non-Goals

**Goals:**
- A script anyone can follow in about half an hour, step by step, with what to check at each step and what counts as a failure.
- Failures that arrive as a report that says what went wrong: the step, what was expected, what was seen, and the app's diagnostic report.

**Non-Goals:**
- Automating the rehearsal: that's the CI smoke test.
- Windows and Linux: the roadmap item is about macOS; the smoke test covers the other platforms later.
- "Check installation" and `shogun doctor`: they're a separate roadmap item; the script can add a step for them when they ship.

## Decisions

**A new account each time, deleted afterwards.** A standard (not administrator) account created for the rehearsal and deleted with its home folder when done, instead of one kept for the purpose: a kept account accumulates `~/.shogunai`, `~/.claude`, `~/.local/bin` and shell profile changes from the previous rehearsal, which is exactly the state the rehearsal must not have. Creating it from System Settings › Users & Groups needs no command line; `sysadminctl` is given as the alternative.

**What "clean" means, and its limits.** The account has no `~/AGENTS.md`, `~/.claude`, shell profile changes or Homebrew `PATH` entry of its own. What's installed machine-wide stays visible to it: Homebrew in `/opt/homebrew` (and its `shogun` if the Cask is installed), the Xcode Command Line Tools' Git, apps in `/Applications`. The script says so and asks to install the app from the `.dmg` into the account's own `~/Applications`, so it doesn't replace the developer's copy, and to check `which shogun` in the new account before starting; the smoke test on a clean runner is what covers a machine with nothing installed.

**Which `.dmg`.** Before a release, the one built from the release branch by the manual `macOS installer` workflow (`gh workflow run macos-installer.yml --ref release/X.Y.Z -f version=X.Y.Z`), signed and notarized like the published one, so a failure blocks the release before it reaches anyone. Before a demo, the latest published release. Installing it from the `.dmg`, not with Homebrew, is deliberate: the Cask links `shogun` itself and would hide the app's own installation of the command.

**Claude Code in the account.** The tour's last step opens Claude Code, which needs to be installed and signed in in the account; the script installs it with its official installer and signs in, with nothing else configured, so the hooks the app installs are the only ones.

**A sample project of its own.** Besides the tour's sample repository, the script creates a small repository with an untracked `local.properties` declared as a local file in the app, and runs `shogun new` from Terminal, because that's what failed in the demo: local files copied into the new worktree.

**Reporting a failure.** Stop at the first failure, note the step number, what was expected and what was seen, take a screenshot, copy Help › Copy Diagnostic Report and paste it into a GitHub issue together with the output of the command that failed. The report is already redacted; the script reminds to check that nothing private is left in screenshots before posting. Before a release, a failure blocks merging the release pull request until it's fixed or the maintainer decides, in the issue, that it doesn't block.

**Placement.** A page of its own in `docs/process/`, next to the release process, linked from step 3 of the release flow ("Open the PR") with one sentence, since it's done while the release pull request is open. Keeping the release pages' edit to one line limits conflicts with `feature/packaged-smoke-test`.

## Risks / Trade-offs

- [The script drifts from the app's labels as the UI changes] → It names menus and buttons as the user guide does and links to the guide's pages for details, so a label change shows up in the guide's review; the rehearsal itself reveals any drift.
- [Machine-wide installs make the account less clean than a new Mac] → Stated in the script; the CI smoke test covers the clean machine.
