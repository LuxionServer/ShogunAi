# Design

## Context

See proposal.md for motivation. `CreateWorktreeUseCase` already takes an optional `startPoint` (the CLI resolves `--from` with `ResolveStartPointUseCase` to a commit). The desktop holder calls it without one, so `git worktree add -b` starts at the base repository's `HEAD`. `ListLocalBranchesUseCase` lists `refs/heads` with whether each is checked out. `ResolveMainBranchUseCase` already resolves the project's main branch, preferring the `origin/` tracking ref. Since `process-robustness`, commands never wait for input and accept timeouts.

## Goals / Non-Goals

**Goals:**
- One branch listing for both modes, read once when the dialog opens.
- Fetching is explicit and visible, never implicit.

**Non-Goals:**
- Remotes other than `origin`; a repository without `origin` simply shows no remote branches and no fetch option.
- Tags or arbitrary commits as a start point in the dialog (`shogun new --from` keeps covering them).
- Choosing the base from the command palette's quick creation; it keeps the default.

## Decisions

**Default start point is the project's main branch.** Most new work branches off `develop`/`main`, not off whatever the base repository has checked out. The default uses `ResolveMainBranchUseCase`'s ref (`origin/develop` when it exists, else the local branch). When no main branch resolves, the default is the base repository's `HEAD`, which is today's behavior. Alternative: keep `HEAD` as the default and make the selector opt-in; rejected because the stale-`HEAD` case is the reason for the change. The selector shows the default first, then "Current branch of the repository (<name>)", then local and `origin/` branches grouped by domain as the existing dropdown does.

**Start point passed as the ref name, resolved at creation.** The dialog passes the selected ref; the holder resolves it with `ResolveStartPointUseCase` after the optional fetch, so a fetch that moves `origin/develop` is honoured. A ref that no longer resolves fails with `StartPointNotFound` in the dialog.

**Fetch.** `FetchRemoteUseCase` runs `git fetch --prune origin` in the base repository with a network timeout (`CommandTimeouts.NETWORK`, new, 60 s) and `GIT_TERMINAL_PROMPT=0` so credential prompts fail instead of hanging. The option is shown only when the repository has an `origin` remote. Its last value is stored per project in `ProjectConfig.fetchBeforeCreate` (default `false`). A failed fetch stops the creation and shows the error in the dialog with "Create without fetching" and "Retry". After a successful fetch the branch listing is read again.

**Remote branches.** `ListBranchesUseCase` returns `BranchOption(name, isCheckedOut, isRemote)` for `refs/heads` and for `refs/remotes/origin/*` except `origin/HEAD`. In "Existing branch", a remote branch is listed only when no local branch has its short name; choosing `origin/feature/x` creates `feature/x` with `git worktree add --track -b feature/x <path> origin/feature/x`, at the folder the local branch would get, and the rollback deletes `feature/x` because the creation made it. In the CLI, `--branch feature/x` falls back to `origin/feature/x` when no local branch exists; `--branch origin/feature/x` is accepted too and means the same.

**CLI `--fetch`.** Runs the same fetch before resolving `--from` or `--branch`; a failed fetch exits with code 1 and creates nothing.

## Risks / Trade-offs

- [Changing the default start point surprises someone who relied on `HEAD`] → The selector shows the chosen base before creating, and "Current branch of the repository" is one click away.
- [Many remote branches make the dropdown long] → Grouped and collapsed by domain, as local branches already are.
- [Fetch needs credentials the app can't ask for] → Fails fast with Git's message; the user fetches in a terminal once or unchecks the option.
