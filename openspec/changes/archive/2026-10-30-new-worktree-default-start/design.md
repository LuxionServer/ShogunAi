# Design

## Context

`WorktreeBoard.startOptions` lists the project's main branch (`ProjectWorktrees.defaultStartRef`, its `origin/` ref when it exists) first, then `StartOption.Current`, then the other branches. `effectiveStartFrom` falls back to `defaultStartRef` when the user hasn't chosen, and to `StartOption.Current` only when no main branch resolves. `StartOption.Current` creates the worktree without a start point, so the branch starts at the base repository's `HEAD` and its recorded base is that repository's current branch, exactly what `shogun new` does from the main checkout.

## Goals / Non-Goals

**Goals:**
- The dialog's default matches `shogun new`.
- The main branch is still one click away.
- The user can tell the default is the CLI's behaviour.

**Non-Goals:**
- Remembering the last chosen start point per project.
- Changing `shogun new`, its `--from`, or how the base is recorded.

## Decisions

- **Default is always `StartOption.Current`.** `effectiveStartFrom` becomes `startFrom ?: StartOption.Current`. The main branch no longer decides the default, so the "no main branch" case collapses into the general one.
- **Order: current, main, others.** `startOptions` puts `StartOption.Current` first and the main branch second (still labelled "(main branch)" and outside the branch groups), so the default sits on top and the old default stays next to it.
- **Hint in the option's label.** "Current branch of the repository (develop) · same as shogun new" (Spanish "… · igual que shogun new"), shown both in the field and in the list. A separate help line under the field was considered, but `DropdownSelector` has no supporting text and the hint belongs to that option, not to the field.
- **Detached base repository.** The label already reads "Current commit of the repository"; it gets the same hint, since `shogun new` also starts from a detached `HEAD`.

## Risks / Trade-offs

- Users used to the main branch as default now have to pick it → it is the second entry of the list, and the CLI already behaves this way.
