# Using with agents

AI agents increasingly create their own worktrees. These recipes make the worktrees they create ready to build, with the files your repository declares in `.shogunai.json` (and `.worktreeinclude`) and your project's local files, and keep them visible and cleanable in ShogunAi. They all rely on the [`shogun` command](architecture/cli.md) being on your `PATH`.

## Per-worktree environment

A worktree isolates files, not ports. When the repository declares variables in `.shogunai.json`, each worktree gets its own values:

```json
{
  "env": {
    "PORT": "${port}",
    "API_URL": "http://localhost:${port:api}",
    "APP_SUFFIX": "-${worktree}"
  },
  "envFile": ".env.shogunai"
}
```

- `${port}` reserves a port for that variable and `${port:<key>}` one shared by every variable using the key. Ports start at 20000, are unique among the repository's worktrees, are free when reserved and never change afterwards. They're kept in Git's own folder for the worktree, so removing the worktree releases them.
- `${worktree}` is the worktree's folder name; `$$` is a literal `$`.
- `shogun env` prints the current worktree's variables as `export` lines (`--format dotenv` or `json` for other tools), so `eval "$(shogun env)"` sets them in a shell.
- `envFile` (optional) is written into each worktree when it's created or provisioned, in dotenv format, for tools that read one. It's never written over a file Git tracks, so add it to `.gitignore`.

The recipes below show how each agent gets these variables.

## Claude Code

Claude Code's `WorktreeCreate` hook **replaces** its own worktree creation for `claude --worktree`, `EnterWorktree`, subagents with `isolation: "worktree"` and background sessions. Point it at `shogun`, together with `WorktreeRemove` so cleanup goes through ShogunAi too. In the repository's `.claude/settings.json` (shared with the team) or your `~/.claude/settings.json` (every repository):

```json
{
  "hooks": {
    "WorktreeCreate": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }
    ],
    "WorktreeRemove": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }
    ],
    "SessionStart": [
      { "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }
    ]
  }
}
```

What happens then:

- `claude -w TASK-123` asks `shogun` for the worktree. It's created on `feature/TASK-123` in your ShogunAi project's worktrees folder, or in `.claude/worktrees/` (Claude Code's usual place) when the repository has no project, and provisioned. Claude Code starts the session inside it.
- Running `claude -w TASK-123` again reuses that worktree.
- When Claude Code removes the worktree (you choose to remove it when exiting, or it had no changes), `shogun` removes it and deletes its branch only if it's merged; a branch with work is kept.
- With this hook Claude Code no longer processes `.worktreeinclude` itself; ShogunAi reads it instead, so its files are still copied.
- `SessionStart` appends the worktree's `env` variables to the session's `CLAUDE_ENV_FILE`, so every Bash command Claude runs sees them (`$PORT`), and tells Claude which values this worktree uses. It works for any session started inside a worktree, also without `--worktree`, and does nothing in repositories without `env`.

The new worktree starts from the main checkout's current commit, like worktrees created from ShogunAi, and Claude Code's `worktree.baseRef` setting doesn't apply. Checked with Claude Code 2.1.301.

## Cursor

Cursor's parallel agents run the setup commands in `.cursor/worktrees.json` inside each new worktree. Provision it there:

```json
{
  "setup-worktree": ["shogun provision"]
}
```

Add your other setup commands (`npm ci`, …) after it. Cursor creates and names the worktree itself; ShogunAi lists it as created elsewhere. Setup commands don't leave variables in the agent's shell, so for the per-worktree environment declare an `envFile` (provisioning writes it) or start commands with `eval "$(shogun env)" && …`.

## Codex

Codex's local environments run a setup script when it creates a worktree for a new thread. Use `shogun provision` as (or at the start of) that script. Codex's documentation doesn't say which directory the script runs in; if it isn't the new worktree, pass its path: `shogun provision <worktree path>`. This recipe hasn't been verified with Codex yet. As with Cursor, use an `envFile` or `eval "$(shogun env)"` for the per-worktree environment.

## Any other agent or tool

- **Let ShogunAi create the worktree, then start the agent in it:**

    ```bash
    cd "$(shogun new TASK-123)" && codex
    ```

- **The tool creates the worktree and can run a command afterwards:** run `shogun provision <worktree path>` (or plain `shogun provision` from inside it).
- **Per-worktree environment in your own shell:** with [direnv](https://direnv.net), commit an `.envrc` containing `eval "$(shogun env)"`; every worktree loads its own values when you `cd` into it. Without direnv, run `eval "$(shogun env)"` yourself.
- **Clean up whatever accumulated, from any tool:** `shogun clean` lists merged worktrees and worktrees whose folder is gone; `shogun clean --yes` removes them.
