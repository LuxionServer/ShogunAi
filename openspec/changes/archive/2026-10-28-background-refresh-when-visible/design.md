# Design

## Context

`AppShell` feeds `LocalWindowInfo.isWindowFocused` into `WorktreeBoard.setWindowActive`, and `startWatching` suspends on `windowActive.first { it }` while it is false. The main `Window` in `main.kt` owns the state that says whether it is shown: `windowVisible` (false when closed to the tray) and `windowState.isMinimized`, which Compose keeps in sync with the AWT frame's iconified state. macOS's Cmd+H hides the whole app without iconifying the frame or changing its visibility; AWT reports it only through `java.awt.desktop.AppHiddenListener`, the same `Desktop` API `installMacAppMenuHandlers` already uses for reopen events.

## Goals / Non-Goals

**Goals:**
- Checks keep running while the window is visible on any monitor, focused or not.
- No check, and so no process, while the window is minimized, in the tray, or the app is hidden, apart from the Gradle daemon trim.

**Non-Goals:**
- Detecting a window that is fully covered by other windows.
- Changing the light refresh on focus, the 3-second or 10-second intervals, or what a check reads.

## Decisions

**Shown, not focused.** `main.kt` computes `windowShown = windowVisible && !windowState.isMinimized && !appHidden` and passes it to `AppShell`, which calls `board.setWindowActive` whenever it changes. Focus no longer takes part. `AppShell` takes it as a parameter with `true` as default, so its other callers (UI tests) keep today's watching behavior.

**App hidden from `Desktop` events.** A small `rememberAppHidden()` in the shell package registers an `AppHiddenListener` when `Desktop.Action.APP_EVENT_HIDDEN` is supported, sets the state on `appHidden`/`appUnhidden`, and removes the listener on dispose. Where it isn't supported, the state stays `false`, so "unknown counts as active" holds.

**The machine section pauses too, except the Gradle trim.** Validating the board pause showed `docker` and `simctl` still running while minimized: they came from `MachineResources`, whose 5-second loader for the shown view and 30-second summary tick never looked at the window. `MachineResources` gets the same `setWindowActive`. The loader suspends on it like the board. The summary tick, while inactive, only calls the Gradle daemon trim, which the "Trim idle daemons to the pool limit" requirement attaches to the summary refresh: pausing it would let agents exceed the pool while nobody looks, which is exactly when they work alone. The tick waits for its interval or for the window to be shown again, whichever comes first, so the summaries are current as soon as the window reappears. Alternative: pause everything, trim included; rejected for that reason, and `shogun pool trim` is manual.

**`refreshOnFocus` unchanged.** It still runs when the window regains focus. With the watcher running while unfocused it rarely finds anything stale, and its 5-second throttle keeps it from duplicating a recent check. Coming back from minimized or hidden also gives focus back in practice, so the catch-up after a pause still happens.

**Rejected: real occlusion detection on macOS.** Asking AppKit through JNA whether the window is occluded (`NSWindow.occlusionState`) would also pause checks when another window covers it entirely. Rejected: it adds a native dependency and platform-specific code for a small saving, and a covered window is often uncovered without any focus change, which would leave it stale until the next check anyway.

**Rejected: keep focus and add a slower background interval.** A 30-second interval while unfocused would still leave a visible window lagging, which is the problem being fixed.

## Risks / Trade-offs

- [A visible window costs the same polling as a focused one] → Accepted: that cost was already acceptable while focused, and `background-refresh-cost` cut it to shown projects and cached device reads.
- [The window is covered by others and still polls] → Accepted, see the rejected occlusion alternative.
- [A platform doesn't report minimized or hidden states] → It counts as shown, so behavior falls back to always watching, as on platforms without focus reports before.
