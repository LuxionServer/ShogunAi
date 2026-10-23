# Proposal

## Why

The Claude Code hooks ShogunAi installs run `shogun hook ...`, so the `shogun` command must be on `PATH`. Whether it is depends on how the app was installed: Homebrew and Scoop provide it, but the `.msi`, winget and the `.deb` do not, and the user only learns that from a short warning buried in Preferences. A Windows user with a manual install could not tell that something was needed before installing the hooks, and the "Install" button was easy to miss. The app's own "is `shogun` on PATH?" check also looks for the exact file name `shogun`, which on Windows never matches `shogun.cmd` or `shogun.exe`, so it can warn when the command works and stay quiet when it doesn't.

It must not matter how the user installed ShogunAi: from the app, installing the hooks has to end with hooks that work.

## What Changes

- The app can make the `shogun` command available by itself, with one action ("Install command-line tool") that works for every install method and needs neither Java nor a download: it uses the app's own bundled runtime.
- The app detects `shogun` reliably on every operating system, honoring Windows executable extensions, and tells apart "not installed", "installed but not on PATH yet" and "available".
- Next to every "Install hooks" action the app shows the command-line tool as a prerequisite with its state and the action to meet it. "Install" for the hooks stays disabled until the command is available, and says why.
- The hooks suggestion in the board and the hooks section in Preferences become more prominent: a stronger notice with a primary action, still dismissible, and a primary-styled button in Preferences.
- When the app cannot install the command itself (running from a build that isn't a packaged install), it shows concrete manual steps for the user's operating system instead of a generic warning.
- README, installation guide and desktop-app guide (English and Spanish) explain the prerequisite and the new action.

## Capabilities

### New Capabilities

- `command-line-tool-setup`: The desktop app detects whether the `shogun` command is available and lets the user install it from the app, whatever the install method.

### Modified Capabilities

- `claude-code-hooks`: "Hooks in the desktop app" and "Suggest the hooks once" require the command-line tool before the hooks can be installed from the app, and make the install action prominent.

## Impact

- `core`: `ExecutableLocator` (Windows extensions), a new use case and infrastructure to detect the command and create/remove its launcher.
- `desktopApp`: `AppContainer`, `ClaudeHooksState`, `ClaudeHooksViews` (Preferences section and board notice), `Strings`/`EnglishStrings`/`SpanishStrings`, tests (`ClaudeHooksUiTest` and new ones).
- Docs: `README.md`/`README.es.md`, `docs/guide/installation*.md`, `docs/guide/desktop-app*.md`, `docs/architecture/ui*.md` and `infrastructure*.md`.
- On Windows, the action adds one folder to the user's `PATH` (current user only, no elevation). On macOS and Linux it creates a symbolic link in a user folder. Nothing is changed unless the user activates the action.
