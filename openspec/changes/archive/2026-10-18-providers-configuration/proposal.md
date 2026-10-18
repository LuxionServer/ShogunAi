# Proposal

## Why

Providers (Gradle, Android emulator, iOS simulator, Xcode), the per-worktree environment (`env`) and the environment file (`envFile`) can only be declared by editing the repository's `.shogunai.json` by hand, with formats few users will remember (`"2g"`, `"${port}"`, `"ios-simulator"`). This is the roadmap's "Providers configuration screen" (`docs/roadmap.md`). Inside the epic it becomes a "Repository configuration" section of the project settings built by `project-settings-view`, writing the repository's file the way the "move local files" action already does.

## What Changes

- A "Repository configuration" section in the project settings, editing `.shogunai.json`:
  - **Providers**: a switch per provider (Gradle, Android emulator, and iOS simulator and Xcode on macOS) with its options. Gradle has daemon heap, idle timeout and Kotlin daemon heap. The emulator has the preferred AVD. The simulator has the preferred device and runtime. Xcode has compilation caching.
  - **Environment**: a list of variables (name and template) with help on the `${port}`, `${port:key}` and `${worktree}` templates, and the environment file path.
- Values are validated as they're typed, with the same rules as when the file is read (memory sizes, positive minutes, variable names, a relative `envFile` inside the worktree).
- Saving writes `.shogunai.json` together with the project's own settings, keeping every field and pattern the section doesn't edit. Before the first write to a repository, the user sees the resulting file and is reminded that it belongs to the repository and should be committed.
- A malformed `.shogunai.json` disables the section with an explanation, so the app never overwrites a file it couldn't read.
- The machine-only settings (chosen AVD, headless mode, simulator device and runtime per machine) stay in their sections and are clearly labeled as "this machine only".

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `project-configuration`: a "Repository configuration" section to turn providers on and set their options, edit the per-worktree environment and the environment file, with validation, a preview before the first write, and labeling of machine-only settings.
- `repository-provisioning`: the app writes provider declarations, `env` and `envFile` into `.shogunai.json`, keeping the fields and patterns it doesn't edit, and never writing over a file it couldn't read.

## Impact

- `core`: a `RepositoryConfigWriter` (extending `RepositoryProvisioningFiles`' atomic write) that updates `providers`, `env` and `envFile` while keeping unknown fields and the existing order. The validation rules that the readers already have are exposed so the form reuses them.
- `ui/projectconfig/`: the new section and its state in `ProjectConfigViewModel`, counted as unsaved changes. The preview dialog.
- After saving, `WorktreeBoard` reloads the project, so the views pick up the new providers. `MachineResources` re-reads `declaredProviders`.
- Docs: `docs/architecture/ui.md`, the repository configuration reference in the docs (both languages), and the README section about `.shogunai.json`.
