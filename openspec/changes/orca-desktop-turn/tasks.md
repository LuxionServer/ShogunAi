# Tasks

## 1. Recognizing Orca's computer use

- [ ] 1.1 Add `OrcaComputerUse.drivesDesktop(command)` in `core/domain/model`, per design.md ("Filter… then parse" and "Read-only commands"). Verify: `OrcaComputerUseTest` covers every acting subcommand, each read-only one, `get-app-state --restore-window`, an absolute path, `orca-ide` and `orca.exe`, quoted words, compound commands with `&&`, `;`, `|` and `$(…)`, a command without `computer`, `orca --help` and `echo computer science`.

## 2. Hooks with a matcher and a filter

- [ ] 2.1 Turn `ClaudeCodeHooks.DESIRED` into `DesiredHook(event, command, matcher, condition)`, compare command, group `matcher` and hook `if` when diffing, write them when installing, and add `PreToolUse` → `shogun hook desktop-turn` with `matcher: "Bash"` and `if: "Bash(*computer *)"`. Move the "event no longer used" test off `PreToolUse`. Verify: `ClaudeCodeHooksTest` covers the scenarios "Desktop turn hook filtered", "Installation without the desktop turn hook" and "Filter changed by hand", and the existing tests pass.

## 3. The `desktop-turn` hook

- [ ] 3.1 Add `hook desktop-turn` to `CliCommand`, `ShogunCli` and `CliHelp`: read the `PreToolUse` input, ignore tools other than `Bash` and commands `OrcaComputerUse` doesn't flag, resolve the `cwd`'s worktree, acquire the turn without waiting, print the `deny` JSON with the holder, expiry, waiters and `shogun desktop acquire --wait` on `DesktopTurnTaken`, and exit 0 silently on any other outcome. Verify: `HookCommandsTest` covers every scenario of the three new `desktop-turn` requirements, including that a renewal extends the expiry, that a waiter stays first in line and that the output is never an `allow`.

## 4. Workflow guide

- [ ] 4.1 Name `orca computer` in the `resources.desktop-turn` rule. Verify: `WorkflowRulesTest` asserts the rule mentions `orca computer`, and the session-start line count test still passes.

## 5. Documentation and roadmap

- [ ] 5.1 Document the hook in `docs/agents.md` (Claude Code's hooks list and the Orca section) and `docs/guide/cli.md` (`hook desktop-turn` and the desktop turn paragraph), with their Spanish pages. Verify: both languages have the same sections; `mkdocs build --strict` passes.

## 6. Validation

- [ ] 6.1 Run `./gradlew test`. Verify: it passes without warnings.
- [ ] 6.2 Check it for real with Orca 1.4.243 and Claude Code: install the hooks from this branch's `shogun` into a scratch settings file, and in a sample worktree have Claude run `orca computer` commands (on the secondary screen) with the turn free, held by the same worktree and held by another worktree. Verify: free and own turn let the command run and `shogun desktop status` shows the worktree holding it with a renewed expiry; another holder blocks it with the instructions; `get-app-state` without `--restore-window` runs while another holds it; the turn is released when the response ends.

## Workflow follow-up

- Archive with `/opsx:archive`, removing the item from `docs/roadmap.md` and `docs/roadmap.es.md` and adding the follow-up for other computer-use tools.
- Implementation commit, rebase on `origin/develop`, PR against `develop` assigned to CiroFeliu.
