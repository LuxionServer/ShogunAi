# Tasks

## 1. CLI

- [ ] 1.1 Turn `CliCommand.Status` into `data class Status(val json: Boolean = false)` and parse `status [--json]` in `CliCommand.kt` with a `parseStatus`, rejecting other options and positional arguments; verify with `ParseArgumentsTest`: `status`, `status --json`, `status --yaml` and `status extra` (the last two are usage errors).
- [ ] 1.2 Add `cli/src/main/kotlin/app/luxion/shogunai/cli/StatusJson.kt` with a top-level `statusJson(repository, mainBranch, entries)` building the document described in `design.md` with `buildJsonObject`; reuse the origin labels of `WorktreeOrigin.label` (move that extension where both can use it, without duplicating the strings).
- [ ] 1.3 Make `ShogunCli.status(command)` collect each worktree with its status and origin once, then print either the existing `statusLine`s or the JSON document (compact, one line, trailing newline); verify with `ShogunCliTest`: `status --json` on `repositoryExecutor()` prints exactly one line that parses as JSON and equals the expected document (main worktree with `origin: null`, merged worktree with `merged: true` and `origin: "shogunai"`, dirty locked worktree with `uncommittedChanges: 2`), and the existing text-output tests still pass unchanged.
- [ ] 1.4 Cover the null cases in `ShogunCliTest`: a prunable worktree gives `folderMissing: true` and `status: null`; a detached worktree gives `branch: null`; a worktree inside Herdr's folder gives `origin: "herdr"`.
- [ ] 1.5 Add `--json` to the `status` line of `USAGE`; verify the help test.

## 2. Docs and validation

- [ ] 2.1 Document `--json` in `docs/guide/cli.md` under `status` (an example document, the meaning of each field and the stability contract) and in the summary at the top; note in `docs/architecture/cli.md`'s `status` row that the JSON is built in `StatusJson.kt` and its field names are a contract. Update both `.es.md` versions.
- [ ] 2.2 In `docs/roadmap.md` and `docs/roadmap.es.md`, reword the "Command-line output for scripts" item to say `status` has `--json` and other commands get it when a script needs it; keep the section.
- [ ] 2.3 Run `./gradlew test` and `mkdocs build --strict`, then check by hand in this repository: `./gradlew :cli:run --args="status --json"` piped through `jq .` and `jq '.worktrees[].branch'`.

## Workflow follow-up

- Archive the change (`/opsx:archive cli-status-json`) once validated, in the same branch, before opening its pull request against `develop`.
