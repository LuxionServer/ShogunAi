# Configuration

ShogunAi reads two kinds of configuration: what a **repository** declares for everyone who clones it, versioned in the repository, and what **this machine** stores for you under `~/.shogunai/`.

## Repository configuration (`.shogunai.json`)

A `.shogunai.json` at the repository's root declares what every worktree needs. Every key is optional, and a blank file counts as `{}`:

```json
{
  "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"],
  "link": ["/keys/"],
  "hooks": { "postCreate": [["npm", "install"], ["direnv", "allow"]] },
  "env": {
    "PORT": "${port}",
    "API_URL": "http://localhost:${port:api}"
  },
  "envFile": ".env.shogunai",
  "providers": {
    "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 },
    "emulator": { "avd": "Pixel_8_API_35" }
  }
}
```

| Key | Type | Meaning |
|---|---|---|
| `copy` | list of strings | Gitignore-style patterns. Every file Git doesn't track that matches one is copied into each new worktree, at the same path. |
| `link` | list of strings | The same patterns as `copy`, but each matching file is linked with a symbolic link to the base repository's file instead of copied, so rotating it there updates every worktree. A file matching both lists is linked. Where symbolic links can't be created (Windows without the privilege), the file is copied instead. |
| `hooks.postCreate` | list of commands | Commands run inside each worktree after it is created or provisioned, once its files are in place and its environment prepared. Each command is a list of strings given as argv, not a shell string; a single list of strings is read as one command. |
| `env` | object of strings | Per-worktree environment variables. Names use letters, digits and `_`, and don't start with a digit. Names that change how shells, runtimes, the dynamic loader, Git, package managers, compilers, build tools, network clients or coding agents behave are reserved, compared without regard to case, and make the file invalid even if approved: see [reserved names](#reserved-variable-names). Templates can't contain line breaks, other control characters or invisible Unicode format characters. The variables only apply once the repository is [approved](#approving-a-repository). |
| `envFile` | string | A path inside the worktree where its environment is written in dotenv format, without control or invisible format characters. Never written over a file Git tracks, so add it to `.gitignore`. Only written once the repository is [approved](#approving-a-repository). |
| `providers` | object | The [providers](providers.md) the repository uses, each with its own options. |

Values in `env` can use these templates:

| Template | Becomes |
|---|---|
| `${port}` | A port reserved for that variable. |
| `${port:<key>}` | A port shared by every variable using the same key. The other repositories of a multirepo can refer to it as `${port:<alias>.<key>}` in its [shared variables](multirepo.md#shared-variables). |
| `${worktree}` | The worktree's folder name. |
| `$$` | A literal `$`. |

Ports are unique among the repository's worktrees, free when reserved, and never change afterwards. [Using with agents](../agents.md#per-worktree-environment) explains how agents and shells get these variables.

Linking shares the file: a tool in one worktree that overwrites a linked secret overwrites the base repository's too. Keep `copy` for files a worktree may change. The project's local files are copied unless you turn on Link for an entry in the desktop app; a linked local folder is one symbolic link to the base folder, so files added to it or deleted from it show up in every worktree at once.

Files a copied local folder no longer has are removed from the worktree the next time it's provisioned: ShogunAi remembers, in the worktree's Git admin folder, which files it copied from each local folder, deletes only those, and never touches files the worktree created itself. `shogun provision --check` reports them as `deleted in base` first. Symbolic links inside a copied local folder are skipped, and provisioning says which ones.

Post-creation commands run in order and stop at the first one that fails. A failure fails creation or provisioning with an error naming the command, its exit code and its error output, but the worktree is kept, since its checkout and files are fine. The commands run on creation and on every `shogun provision`, so make them safe to repeat, and only declare commands you would run from a repository you trust. They don't receive the worktree's environment variables; a command that needs them can run `shogun env`.

### Approving a repository

Because `.shogunai.json` is committed, whoever controls a branch controls its post-creation commands and the variables it declares in `env`, which reach your agent's session, your terminals and the environment file. They therefore apply only after you have approved them on this machine, for that repository, in one approval that covers the commands, every declared variable with its template, and `envFile`. The approval covers exactly what you saw: if a pull changes, adds or reorders a command, adds, removes or changes a variable, or changes `envFile`, the repository counts as not approved and ShogunAi asks again. Reordering the keys of `env` doesn't. A repository that declares no `env` nor `envFile` keeps the approval its commands had in earlier versions. Approvals are stored in `~/.shogunai/trust.json`, readable only by you.

While a repository isn't approved its commands don't run and its declared variables aren't delivered anywhere: not to the agent's session, `shogun env`, terminals, Herdr panes, the environment file nor the desktop app's environment view, which lists them as awaiting approval. Variables added by [providers](providers.md) are still delivered, since ShogunAi computes their values.

- The desktop app shows the commands, the variables with their templates and the environment file in the create dialog, before preparing the worktree's environment, and offers "Approve and remember" or "Skip". Skipping creates the worktree without running the commands, without the declared variables and without the environment file. Project settings lists what the repository declares, whether it is approved on this machine, and lets you approve or revoke it. Saving the environment from project settings approves what you saved, unless the repository has commands you hadn't approved.
- `shogun new` and `shogun provision` print the same and ask on a terminal. Where they can't ask, and always in the agent hooks, they skip them and print a note on standard error that names `shogun trust`.
- `shogun trust` shows the commands, variables and environment file and approves them (`--yes` skips the question) or revokes the approval (`--revoke`); see [the CLI reference](cli.md#trust).

### Reserved variable names

These names can't be declared in `env` nor in the `docker-compose` provider's `portVariables`, in any case (`https_proxy` is as reserved as `HTTPS_PROXY`). A project script can set them itself.

- Shell and session: `PATH`, `HOME`, `SHELL`, `USER`, `LOGNAME`, `IFS`, `ENV`, `BASH_ENV`, `ZDOTDIR`, `PROMPT_COMMAND`, `PS1`, `PS2`, `PS4`, `PROMPT`, `RPROMPT`, `SHELLOPTS`, `BASHOPTS`, `CDPATH`, `FPATH`, `HISTFILE`, `INPUTRC`, `TMPDIR`, `TMP`, `TEMP`, `DBUS_SESSION_BUS_ADDRESS`, and names starting with `XDG_`.
- Pagers, editors and helpers: `PAGER`, `MANPAGER`, `EDITOR`, `VISUAL`, `BROWSER`, `SUDO_ASKPASS`, and names starting with `LESS` or `SSH_`.
- Network and TLS: names ending in `_PROXY`, `SSL_CERT_FILE`, `SSL_CERT_DIR`, `CURL_CA_BUNDLE`, `REQUESTS_CA_BUNDLE`.
- Dynamic loader and Git: names starting with `LD_`, `DYLD_` or `GIT_`.
- Language runtimes and package managers: `NODE_OPTIONS`, `NODE_PATH` and names starting with `NODE_` (except `NODE_ENV`), `NPM_CONFIG_`, `YARN_`, `PNPM_`, `BUN_`, `PIP_`, `PYTHON`, `UV_`, `POETRY_`, `CONDA`, `GEM_`, `BUNDLE_`, `RUBY`, `PERL`, `CARGO_`, `RUSTC` or `RUSTUP_`; `RUSTFLAGS`, `RUSTDOCFLAGS`; the Go variables `GOFLAGS`, `GOPROXY`, `GOPATH`, `GOROOT`, `GOBIN`, `GOENV`, `GOTOOLCHAIN`, `GOSUMDB`, `GONOSUMDB`, `GOPRIVATE`, `GONOPROXY`, `GOINSECURE`, `GOWORK`, `GOCACHE`, `GOMODCACHE`; and the JVM's `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `CLASSPATH`, `MAVEN_ARGS`.
- Compilers and build tools: `CC`, `CXX`, `CPP`, `LD`, `AR`, `AS`, `CFLAGS`, `CXXFLAGS`, `CPPFLAGS`, `LDFLAGS`, `MAKEFLAGS`, `MAKE`, `PKG_CONFIG_PATH`, and names starting with `CMAKE_` or `CCACHE_`.
- Any name ending in `_OPTS` (such as `JAVA_OPTS` or `GRADLE_OPTS`) or `_HOME` (such as `JAVA_HOME`).
- Containers and clouds: names starting with `DOCKER_`, `KUBECONFIG`, `AWS_CONFIG_FILE`, `AWS_SHARED_CREDENTIALS_FILE`, `CLOUDSDK_CONFIG`, `AZURE_CONFIG_DIR`.
- Coding agents and tools: names starting with `ANTHROPIC_`, `CLAUDE_`, `CODEX_`, `GH_`, `HERDR_` or `SHOGUNAI_`, and `OPENAI_BASE_URL`.

Ordinary application names such as `PORT`, `DATABASE_URL`, `NODE_ENV`, `API_URL`, `VITE_*`, `NEXT_PUBLIC_*` or `ANDROID_SERIAL` are allowed.

`copy` and `link` files need no approval: they are only read from the base repository and, after following symbolic links, only written inside the worktree. A file whose destination resolves outside the worktree, through a symlinked folder from the branch, fails the operation.

Provider options:

| Provider | Options |
|---|---|
| `gradle` | `daemonMaxHeap`, `kotlinDaemonMaxHeap`: a size such as `512m` or `2g` (a number followed by `k`, `m` or `g`). `idleTimeoutMinutes`: a positive whole number, not a string. |
| `emulator` | `avd`: the preferred AVD name. |
| `ios-simulator` | `device`, `runtime`: the preferred device type and runtime names. |
| `xcode` | `compilationCache`: `true` or `false`. |
| `maven` | `mavenMaxHeap`: a size such as `512m` or `1g`. |
| `docker-compose` | `portVariables`: a list of unique variable names that follow the `env` name rules and aren't reserved. |
| `desktop-app` | `module` (required): the Gradle path of the app's module, such as `:desktopApp`. `isolatedHome`: `true` (default) or `false`. |

Unknown keys and unknown providers are ignored. A file that isn't valid JSON, or a known key with the wrong type, makes creating and provisioning worktrees fail with a message naming the file and the problem, and nothing is created.

### Editing it from the app

You don't have to write this file by hand. The project settings have a "Repository configuration" section where you turn providers on with their options, edit the `env` variables (with help on the `${port}`, `${port:key}` and `${worktree}` templates) and set the `envFile` path. The section also suggests the providers that fit the files it finds in the repository (see [detecting stacks](providers.md#detecting-stacks)); using a suggestion only turns the switch on, and nothing is written until you save. Values are checked as you type with the same rules as above. Before the first save that writes the file, the app shows the resulting `.shogunai.json` and reminds you that it belongs to the repository and should be committed.

Saving only changes `providers`, `env`, `envFile` and, with "Move to .shogunai.json", `copy`: every other key, unknown provider and unknown provider option stays as it was (keys keep their order, indentation is normalized to two spaces). The app never writes over a file it couldn't read, or one that changed on disk since the settings were opened: in the first case the section explains the problem, in the second it offers to reload. The settings that apply to this machine only (the AVD to use, headless mode, the simulator device and runtime) stay in their own sections, labeled "This machine only".

## `.worktreeinclude`

Claude Code's `.worktreeinclude` is read like `copy`: one gitignore-style pattern per line, skipping blank lines and lines starting with `#`. Both files can be used together.

## Machine files (`~/.shogunai/`)

| File | Contents |
|---|---|
| `projects.json` | The desktop app's project catalog: each project's name, base repository, worktrees folder, main branch, local files, agent commands, the IDE chosen for the project on this machine, and emulator and simulator settings. The `shogun` command reads it to find a repository's project. |
| `settings.json` | The app's preferences: theme (`LIGHT`, `DARK` or `SYSTEM`, the default), language (`ENGLISH`, the default, or `SPANISH`), window bounds, whether the sidebar is collapsed, the collapsed projects, the last destination, the last project used to create a worktree, the recent command palette items, and the grouping of the worktree list per view, and the chosen terminal (`TERMINAL`, `ITERM2`, `WINDOWS_TERMINAL`, `TMUX` or `ZELLIJ`) and default IDE (`VS_CODE`, `INTELLIJ_IDEA` or `ANDROID_STUDIO`), unset until chosen, and the agent last chosen for each worktree. |
| `multirepos.json`, `multirepos.json.lock` | The multirepos known on this machine (the paths of their `.shogunai-multirepo.json`), the approval of each declaration and the multirepo agent set here. Readable only by you. |
| `reservations.json`, `reservations.lock` | An index of the worktrees, in any repository, that have reserved ports, and the lock taken while reserving, so two worktrees never get the same port. |
| `device-queue.json` | The worktrees waiting for an emulator or simulator with `--wait`, in order. It disappears when nobody waits. |
| `logs/emulator-<port>.log` | The output of each emulator ShogunAi started. |
| `xcode/compilation-cache.xcconfig` | The shared Xcode settings written by the Xcode provider's `compilationCache`. |

If `projects.json` or `settings.json` can't be read (for example, it isn't valid JSON), ShogunAi never overwrites it silently: the desktop app shows a notice with the file's path, the `shogun` command fails with one line naming it, and before anything writes a new file the unreadable one is kept next to it as `projects.json.unreadable-<timestamp>` (or `settings.json.unreadable-<timestamp>`). An unreadable `settings.json` makes the app start with the default settings. Writes to both files are serialized between the app and `shogun` with a `projects.json.lock` or `settings.json.lock` file next to them, so two writers never drop each other's change.

These files contain paths of your machine; don't commit them anywhere.

## Per-worktree state

Each worktree's reserved ports and resources (its iOS simulator, for instance) are kept in `shogunai-env.json` inside Git's own folder for that worktree (`.git/worktrees/<name>/`), not in the worktree itself. A headless desktop app keeps its isolated home, its launch log and its captures in `shogunai/headless/` in that same folder. Removing the worktree removes them with it.

## Other locations

- `$GRADLE_USER_HOME/init.d/shogunai-ios-simulator.init.gradle.kts` (`~/.gradle/init.d/` by default): the init script installed by `shogun simulator gradle-init install`.
- Herdr's `config.toml` (`$XDG_CONFIG_HOME/herdr/`, or `~/.config/herdr/`): ShogunAi only reads its `[worktrees] directory`, to recognize worktrees created from Herdr (`~/.herdr/worktrees` when unset).

## Port ranges

| Use | Range |
|---|---|
| `${port}` and `${port:<key>}` | 20000–29999 |
| Android emulator consoles | 5554–5584, even numbers only |
