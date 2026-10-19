# Design

## Context

See proposal.md for motivation. `WorktreeStatus.isMerged` is `mainBranch.ahead == 0 && mainBranch.behind > 0`: a branch created from the main branch, with no commits yet, becomes "merged" as soon as the main branch gets a new commit. `worktreeActivity` then classifies it as `Merged` (or `NeedsYou` when it has uncommitted changes, while the row still shows the merged chip), and `selectCleanup` offers it for removal when it has no changes.

In project settings, the save bar's "Discard" calls `onCancel`, which leaves the view through the shell's leave guard, and the guard asks "Discard changes?" again. The title uses the fixed `projectConfigTitle`. `RepositoryProvisioningList` builds its rows from `copyPatterns` and `worktreeIncludePatterns` only. The machine views render `entry.emulator?.serial ?: "—"` and `entry.simulator?.name ?: "—"` and have no header row.

## Goals / Non-Goals

**Goals:**
- Tell "never had commits" apart from "merged" without network access and with at most one extra Git read per worktree, and only for branches that would read as merged.

**Non-Goals:**
- Detecting squash merges or merges done on the remote only; a branch merged that way keeps reading as not merged, as today.
- Showing post-creation commands in the files list; they keep their own section in "Repository configuration", the list only points to it.

## Decisions

**"Not started" from the branch's reflog.** When a branch would read as merged, read `git reflog show --format=%H refs/heads/<branch>`. The branch never had commits of its own when its reflog has a single entry (its creation) whose commit is the branch's current tip, and the branch has no upstream. A branch with an upstream, a detached HEAD, a missing or expired reflog, or more than one entry keeps today's merged reading. Alternative: compare the tip with the commit the main branch had when the worktree was created; rejected, nothing records that commit for worktrees created by other tools, while Git writes the reflog for every `git branch`/`git worktree add -b`. Requiring no upstream avoids reading a merged remote branch checked out locally (one reflog entry, "Created from origin/…") as not started.

`WorktreeStatus` gains `neverCommitted: Boolean` (default `false`) and `isMerged` becomes false when it is set; a new `isNotStarted` exposes it. `worktreeActivity` treats a not-started worktree without changes as `InProgress` (it's active work just created) and with changes as `NeedsYou`, as today, but without the merged chip. Cleanup needs no change: it already requires `isMerged`.

**Discard without a second question.** The save bar's "Discard" resets the form to the saved project and then leaves without passing through the leave guard (`ShellViewModel` gets a `leaveDiscarding` path that skips the confirmation). Sidebar, shortcuts, menus and closing the window keep the guard. Alternative: keep the dialog and drop the button; rejected, the button is the explicit intent.

**Title.** "Settings · <project name>" for an existing project, using the saved name, not the draft; "Add project" stays for a new one.

**Provisioning list.** Rows become `(pattern, source, linked)`: `link` patterns show "`.shogunai.json` · linked". When the repository declares post-creation commands, a line under the list says how many run after creation and where they're listed; the "declares nothing to copy" text is shown only when there are no `copy`, `link`, `.worktreeinclude` patterns and no commands.

**Machine views.** A shared `MachineColumnHeader` row in `MachineViewParts` with the same widths as `MachineRowLine`: Device, Worktree, State. A worktree emulator that isn't running shows the AVD it will use (the project's AVD setting, then the repository's preferred AVD, then "Automatic"); a worktree simulator that doesn't exist yet shows "Not created" plus the configured device type when set.

## Risks / Trade-offs

- [Reflog disabled (`core.logAllRefUpdates=false`) or expired] → Falls back to today's merged reading, never to "not started", so cleanup can't miss a merged branch because of it.
- [A branch reset back to its creation point after committing] → Its reflog has several entries, so it reads as merged as before.
- [One more Git read during status refresh] → Only for branches that would read as merged, with the existing read timeout.
