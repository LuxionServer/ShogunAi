## Why

`CopyWorktreeLaunchCommandUseCase` hardcodes single-quote shell quoting around the worktree path (`cd '<path>' && ...`). Single quotes are not a quoting mechanism in `cmd.exe` — they are passed through as literal characters — so pasting the copied command into `cmd.exe` on Windows fails to `cd` into the worktree. The command works in bash/zsh and PowerShell today, but not in the shell most Windows users still default to.

## What Changes

- Replace the hardcoded single-quote wrapping in `CopyWorktreeLaunchCommandUseCase` with double-quote wrapping (`cd "<path>" && ...`), which is valid quoting in `cmd.exe`, PowerShell, bash, and zsh alike.
- No OS-detection port is introduced: a single quoting style that is valid everywhere avoids the extra indirection and keeps the fix a one-line behavior change.
- Update the `worktree-terminal-launch` spec's "Successful copy" scenario to reflect the new double-quote format.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-terminal-launch`: the "Successful copy" scenario now specifies double-quote wrapping (`cd "<worktree path>" && <resolved agent command>`) instead of single-quote wrapping, so the copied command is valid in `cmd.exe` in addition to PowerShell, bash, and zsh.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/usecase/CopyWorktreeLaunchCommandUseCase.kt`: change the quote character used when building the command string.
- `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/usecase/CopyWorktreeLaunchCommandUseCaseTest.kt`: update expected clipboard content in existing tests.
- No changes to `ClipboardWriter`, `AwtClipboardWriter`, `ProjectConfig`, or `AgentLaunchConfig`.
