# Design decisions

Log of architecture decisions made during development, along with the reasoning. A new entry is added when a decision is made that isn't obvious from reading the code.

!!! info "Since OpenSpec"
    Since adopting [OpenSpec](../process/openspec.md), each change's design decisions are documented in that change's `design.md` and are preserved when archived under `openspec/changes/archive/`. This page covers what was decided **before** adopting OpenSpec (iteration 1); for later changes, that's the source of truth.

## Iteration 1 — Domain and infrastructure of the worktree flow (2026-07-10)

**All domain and infrastructure code lives in `desktopApp`, not in `shared`.**
Reason: the project's only target is JVM/desktop (`shared` uses `jvm()`). Putting domain logic in a "shared" module with no other real targets only adds indirection.

**`ProjectConfig` is fully configurable** (base repo path, worktrees parent folder, list of secret files).
Reason: to make the flow reusable across different projects without touching use case logic, and without hardcoding names or identifiers specific to one project.

**`BranchType` = `FEATURE` / `FIX`**, with a `feature/` or `fix/` prefix on the task ID.
Covers the branch flow needed today; no more types are added until they're actually needed.

**Use cases return `Result<T>` with typed errors (`WorktreeError`)**, not untyped exceptions or error codes.
Reason: so the future GUI layer can decide what message to show by inspecting the error's type, without parsing strings.

**Domain ports (`ShellCommandExecutor`, `FileManager`) have a real implementation and a test double.**
`ProcessBuilderShellCommandExecutor` runs the command as argv (not `sh -c`) to avoid shell differences between macOS and Arch Linux, and injection/escaping issues. `NioFileManager` uses `java.nio.file`. Both have fakes (`FakeShellCommandExecutor`, `FakeFileManager`) to test use cases without touching disk or launching processes.

**Compose GUI**: implemented in the `project-worktree-ui` change (project catalog, configuration form, worktree management). Design decisions for that iteration are in `openspec/changes/archive/`.
