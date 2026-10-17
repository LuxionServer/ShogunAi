# ShogunAi plugin for Herdr

A rescue path for worktrees [Herdr](https://herdr.dev) creates in repositories managed by [ShogunAi](https://github.com/LuxionServer/ShogunAi). The recommended flow is to create worktrees from ShogunAi and open them with its "Open in Herdr" action; this plugin covers the ones created from Herdr's "New worktree", `herdr worktree create` or its API anyway.

## Install

The plugin calls the `shogun` command, so install ShogunAi first and check that `shogun` is on the `PATH` of the shell you start Herdr from (Herdr's server keeps that shell's environment; restart it with `herdr server stop` after installing `shogun`). Then:

```bash
herdr plugin install LuxionServer/ShogunAi/integrations/herdr
```

## What it does

On Herdr's `worktree.created` event it runs `shogun hook herdr`, which provisions the new worktree exactly like `shogun provision <path>`: the files declared in the repository's `.shogunai.json` and `.worktreeinclude`, the local files of its ShogunAi project, its reserved ports and its environment file. When the repository has a ShogunAi project and the worktree is outside its worktrees folder, it also shows a Herdr notification recommending creating worktrees from ShogunAi.

Each run, with the files it copied or its error, shows up in:

```bash
herdr plugin log
```

See [Using with agents](https://github.com/LuxionServer/ShogunAi/blob/main/docs/agents.md#herdr) for the recommended flow and how the per-worktree environment reaches agents running in Herdr.
