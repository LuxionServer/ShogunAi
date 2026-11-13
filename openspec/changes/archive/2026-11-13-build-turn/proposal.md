# Proposal

## Why

With several agents working in parallel, they all launch their builds and tests at about the same time (ten sessions in one repository running `./gradlew test koverVerify` at once is a normal day). The Gradle provider bounds each daemon's memory and lifetime, but nothing bounds how many heavy builds run at the same time, so the machine swaps, every build slows down and some time out. The machine already has the pieces to share scarce resources cooperatively (a FIFO wait queue, waiting with `--wait`, limits from the capacity profile, a `PreToolUse` hook that turns agents away and tells them what to run); builds only need to use them.

## What Changes

- A machine-wide **build turn**: a number of build slots shared by every repository and worktree, taken by `shogun build run [<options>] -- <command>`, which waits in line for a slot, runs the command in the current directory with the terminal's input and output, gives the slot back when the command ends and exits with the command's exit code. A slot belongs to the `shogun` process running the build, so a crashed or killed build frees it without any expiry.
- `shogun build status [--json]`: the slots in use (project, worktree, command, since when) and the waiters in order.
- The number of slots follows the machine's capacity profile: Light 1, Balanced 2, Automatic from memory and cores (between 1 and 4), One per worktree no limit. Custom uses the Automatic value until Preferences offers its own field.
- A `PreToolUse` hook, `shogun hook build-turn`, installed by `shogun hooks install`, denies a heavy build run without the turn (Gradle, Maven, `xcodebuild`, `swift build|test`, except their light invocations such as `--version` or `--stop`) and tells the agent to run it again through `shogun build run --`, in the background or with a long shell timeout. With no limit it lets every build run.
- `shogun status` shows, per worktree, its running builds and its place in the build queue, in text and JSON.
- `shogun guide resources` gains a rule to run heavy builds through `shogun build run --`. It isn't in the session context, since the hook tells agents when it applies.
- The desktop app's view of the build turn (control tower, Preferences field for Custom) and Maven/Xcode-specific refinements are left as follow-ups in the roadmap.

## Capabilities

### New Capabilities

- `build-turn`: the machine's build slots, `shogun build run` and `status`, the queue, the limit per capacity profile, the build-turn hook and the build note in `shogun status`.

### Modified Capabilities

- `claude-code-hooks`: `shogun hooks install` adds the build-turn `PreToolUse` hooks.
- `agent-workflow-guide`: the `resources` topic gains the build-turn rule, kept out of the session context.

## Impact

- core: new model, port, NIO store and use cases for the build turn next to the desktop turn's; a `BUILD` queue in `device-queue.json` (new key, existing ones unchanged); a build limit next to the other limits in `ResolveResourceLimitsUseCase`; a foreground command runner port; new `DesiredHook` entries; a new workflow rule.
- cli: `shogun build run|status`, `shogun hook build-turn`, the build note in `shogun status`, help text. The parsing and output live in new files; shared files only get the dispatch lines.
- No change to `.shogunai.json`, the desktop app or the existing device and desktop queues.
