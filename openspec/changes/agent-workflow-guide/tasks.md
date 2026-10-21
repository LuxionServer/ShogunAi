## 1. Catalog

- [ ] 1.1 Add the rule model (id, topic, text, condition, inSessionStart), the `WorkflowContext` and `rulesFor`, with the rules of `worktrees`, `parallel`, `resources` and `cleanup`
- [ ] 1.2 Tests: each rule appears where its condition holds and not elsewhere; the short subset fits ten lines in the richest context; each command a rule names parses with `CliCommand`
- [ ] 1.3 Detect the context: main checkout or worktree, Herdr availability, declared providers

## 2. `shogun guide`

- [ ] 2.1 Parse `guide [<topic>] [--plain] [--migrate] [--file <path>]` and the usage error for unknown topics; update help
- [ ] 2.2 Print applicable rules, then inapplicable ones marked with their condition; precedence line first
- [ ] 2.3 `--plain` without reading the directory, and `--migrate` output with ids and the agent instruction
- [ ] 2.4 Tests for each scenario of the guide requirements

## 3. Session start

- [ ] 3.1 Restructure `hookSessionStart`: nothing outside a Git repository, otherwise the variables line (when any) plus the short rules; no write to `CLAUDE_ENV_FILE` without variables
- [ ] 3.2 Move the emulator and simulator hints into catalog rules, keeping the variables in the variables line and the existing hint scenarios passing
- [ ] 3.3 Update `ShogunCliTest` for the new scenarios (main checkout, no Herdr, no env, not a repository)

## 4. Instructions block

- [ ] 4.1 Verify against their documentation where Codex, Cursor and Gemini read user-level instructions; record the results in the design
- [ ] 4.2 Add the use case that renders, installs, replaces, removes and checks the marked block, preserving text outside the markers, using the `FileManager` port
- [ ] 4.3 Parse and run `guide install|uninstall|status` with `--agent`, `--project`, `--file`, `--yes` and `--json`
- [ ] 4.4 Warn about duplicated workflow text and about the hook plus the block both being present
- [ ] 4.5 Tests: idempotence, older block, uninstall, declined, no target, text outside markers untouched, warnings

## 5. Docs

- [ ] 5.1 `docs/agents.md` and `docs/agents.es.md`: what each agent gets (hook for Claude, block for the others) and how to migrate existing instructions
- [ ] 5.2 `docs/guide/cli.md` and `docs/guide/cli.es.md`: the `guide` command and the new `session-start` output
- [ ] 5.3 Check whether the change affects `docs/roadmap.md` and `docs/roadmap.es.md`

## 6. Validation

- [ ] 6.1 `./gradlew test` passes
- [ ] 6.2 Run `shogun guide`, `shogun guide install --file <temp file>` and the hook against a real worktree and check the output
