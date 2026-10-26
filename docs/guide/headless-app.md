# Checking a desktop app in the background

An agent that checks a Compose Desktop app with computer use takes over your screen, mouse and keyboard while it works, and only one agent can do it at a time. On macOS, ShogunAi lets each worktree's agent run its own app in the background instead: the app never comes to the front, its window is hidden, and several worktrees can run their apps at once while you keep working.

## What each part does

| Part | Who runs it | What it does |
|---|---|---|
| `shogun headless start` | The agent | Launches the worktree's app without taking focus, with its own user home, and hides it. |
| Compose Hot Reload's MCP server | The agent, as an MCP server | Reads the app's semantic tree and clicks, types and scrolls through it, in the background. |
| `shogun headless capture` | The agent | Saves a PNG of the app's window, even while hidden or covered. |
| `shogun headless stop` | The agent, the worktree's detail in the desktop app, or the removal of the worktree | Ends the app. |

## Setting up a repository

1. The app must use Compose Multiplatform 1.12 or later, which ships Compose Hot Reload and its MCP server.
2. Declare the app's Gradle module in `.shogunai.json`:

    ```json
    { "providers": { "desktop-app": { "module": ":desktopApp" } } }
    ```

3. Declare the MCP server for the agents, for example in Claude Code's project file `.mcp.json`:

    ```json
    { "mcpServers": { "compose-hot-reload": { "command": "./gradlew", "args": ["-q", ":desktopApp:hotMcpServer"] } } }
    ```

    Started from a worktree, the server finds the app of that worktree only, so each agent drives its own app.

4. If the app shows anything outside its window, such as a menu bar icon, leave it out when the JVM system property `shogunai.headless` is `true`: hiding the app doesn't reach it. ShogunAi itself does this with its menu bar icon.

## Permissions

macOS asks for two permissions, granted to the application that runs `shogun` (your terminal, or the app that runs the agent), not to ShogunAi:

- **Screen Recording** (System Settings › Privacy & Security › Screen Recording), to capture the window.
- **Accessibility** (System Settings › Privacy & Security › Accessibility), to hide the app. Without it the app still runs in the background, visible behind your windows.

`shogun headless status` lists the ones that are missing.

## How the agent works with it

- Start the app with `shogun headless start`; it prints the process id once the window is hidden.
- Read the semantic tree before acting, and read it again after every change of view: node ids change.
- Clicks run the element's action directly, so they don't check what a real click would (overlays, hit areas, hover).
- See the app with `shogun headless capture`, never with the MCP's `take_screenshot`: that one copies the screen region under the window, so with the window hidden or covered it captures whatever is on top, such as another session's terminal.
- The MCP's `restart` shows the window again; run `shogun headless start` after it to hide it.
- The app keeps its data in its own home, kept between starts; `shogun headless start --fresh` starts it as on a first run.

The `SessionStart` hook gives Claude these rules in repositories that declare the provider.

## What still needs the desktop

Real keyboard input doesn't reach an app in the background (the window isn't active, and the toolkit drops key events for it), and neither do keyboard shortcuts, native menus, the menu bar, system dialogs or hover. Checking those still means driving the real desktop with computer use, which the agent does after taking the machine's desktop turn with `shogun desktop acquire --wait`, so only one agent drives the desktop at a time, and giving it back with `shogun desktop release` (see [`desktop`](cli.md#desktop)).

## In the desktop app

A worktree whose app is running shows it in the second line of its row, and its detail shows the process id and the launch log with a "Stop" action. The desktop app only checks the recorded process for this, never the macOS window tools, so it asks for no permission. When removing or cleaning up a worktree can't stop its app, a notice says so until you dismiss it.

## Other platforms

Running an app in the background is only available on macOS for now; on Windows and Linux the `shogun headless` commands say so.
