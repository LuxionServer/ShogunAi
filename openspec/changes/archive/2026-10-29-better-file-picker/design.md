# Design

## Context

`desktopApp/.../ui/FilePicker.kt` defines a `PathPicker` port (`pickDirectory`, `pickFiles`) and a `FilePicker` object that implements it with `JFileChooser`. Project settings read it through `LocalPathPicker`, which the end-to-end tests replace with `AnsweringPathPicker`; `AddMultirepoDialog` and `MultirepoSettingsView` take a `pickFolder: (String, String) -> String?` parameter that defaults to `FilePicker::pickDirectory` and that their tests pass as `{ _, _ -> null }`. All callers invoke the picker synchronously from a click handler, blocking the event thread while the Swing dialog is up.

The packaged app is built by the Compose Gradle plugin with ProGuard on release builds, a jlink runtime with explicit extra modules (`java.net.http`, `java.management`, `jdk.management`), and on macOS the hardened runtime with `allow-jit`, `allow-unsigned-executable-memory` and `disable-library-validation`, but **no App Sandbox**: the app reads any path the user owns, so a folder chosen in a dialog needs no security-scoped bookmark.

Mobile secret detection (`DetectMobileSecretsUseCase`, used by `shogun stacks` and the settings' suggestions) already classifies `local.properties`, signing properties, keystores and Firebase files by name, and skips generated folders. Provisioning (`resolveProvisionedFiles`) already resolves which untracked files the repository's `.shogunai.json` and `.worktreeinclude` copy or link.

## Goals / Non-Goals

**Goals:**
- One native folder dialog behind the existing port, used by all six folder choices, without blocking the UI thread.
- An in-app local file picker whose candidates come from Git, computed in `core` so the same classification serves the app and could serve the CLI later.
- Keep every picker injectable so no test opens a real dialog.

**Non-Goals:**
- A native or in-app picker for choosing files outside a repository; local files are always relative to the base repository.
- A CLI command to list local file candidates.
- Expanding an ignored folder in the picker to choose files inside it; the folder is chosen whole, and a single file inside it can still be typed or matched by a pattern.
- Choosing copy or link inside the picker; the list below keeps that switch.

## Decisions

### FileKit dialogs for the native folder dialog

Use `io.github.vinceglb:filekit-dialogs` 0.17.0 (`FileKit.openDirectoryPicker`). On the JVM it calls `NSOpenPanel` on macOS through JNA on the AppKit main thread, `IFileOpenDialog` on Windows through JNA COM, and the `org.freedesktop.portal.FileChooser` portal on Linux through dbus-java, falling back to a Swing `JFileChooser` when no portal answers. It needs no `FileKit.init` for dialogs, accepts a starting directory and a title, and takes the owner window as `FileKitDialogParent.awt(window)`.

Alternatives considered:
- `java.awt.FileDialog` with `apple.awt.fileDialogForDirectories`: native on macOS only; on Windows and Linux `FileDialog` can't choose folders.
- LWJGL's tinyfiledialogs or nativefiledialog: native binaries per platform for the same result, and tinyfiledialogs shells out to `zenity`/`kdialog` on Linux instead of using the portal.
- Our own JNA bindings: the same code FileKit maintains, without its tests across platforms.

Fit with the packaged app:
- macOS: no sandbox, so no `com.apple.security.files.user-selected.*` entitlement is needed. JNA extracts and loads `libjnidispatch`, which the existing `disable-library-validation` and `allow-unsigned-executable-memory` entitlements already allow, and the Compose plugin signs native libraries inside jars when signing is on, as notarization requires.
- Linux: dbus-java's native Unix socket transport uses `java.net.UnixDomainSocketAddress` from `java.base`; the portal works on Wayland and X11 and gives the GNOME or KDE dialog.
- The jlink image adds `jdk.security.auth` (dbus-java's Unix login to the session bus) and `jdk.net` (its native Unix socket transport). `jdeps` on dbus-java found both; the plugin's `suggestRuntimeModules` only reports the first, and without them the portal would fail at runtime and the picker would silently fall back to Swing.
- ProGuard: FileKit ships no consumer rules, so `proguard-rules.pro` keeps JNA (`com.sun.jna.**` and implementors of its interfaces), FileKit's platform bindings and dbus-java (`org.freedesktop.dbus.**`), which are reached by reflection.

### A suspending `PathPicker` with only folders

`PathPicker` becomes `suspend fun pickDirectory(initialPath: String, dialogTitle: String): String?`; `pickFiles` is removed. `NativeFolderPicker(owner: () -> Window?)` implements it with FileKit, passing the starting folder only when it exists, and on any exception from FileKit falls back to the current `JFileChooser` code, kept as `SwingFolderPicker`. A `Mutex` drops a second request while a dialog is open, since the Linux portal doesn't block the owner window.

`Main` provides `LocalPathPicker` inside the main `Window`, with the window as owner. `AddMultirepoDialog` and `MultirepoSettingsView` keep their `pickFolder` parameter, now `suspend (String, String) -> String?`, defaulting to `LocalPathPicker.current::pickDirectory`; their tests' `{ _, _ -> null }` lambdas still compile. Callers launch the picker in a `rememberCoroutineScope`, so the event thread keeps painting while the dialog is open. Suspending was chosen over `runBlocking` because FileKit already moves to `Dispatchers.IO` and, on macOS, hops to the AppKit thread; blocking the AWT thread on that would freeze the window and risk a deadlock.

### Local file candidates in `core`

A new `ListLocalFileCandidatesUseCase(executor, fileManager, provisioningSource)` returns `List<LocalFileCandidate(path, isFolder, secretKind: MobileSecretKind?, providedByRepository: Boolean)>` for a `ProjectConfig`:
- `git ls-files -z --others --exclude-standard --directory --no-empty-directory` (untracked) and the same with `--ignored` (ignored). With `--directory`, a folder that is untracked or ignored as a whole comes back once, ending in `/`, which becomes `isFolder`.
- Entries in or under the generated folders are dropped, using the same set as mobile secret detection.
- `secretKind` uses the same name classification as `DetectMobileSecretsUseCase`. Both move to one internal place in `core` (`MobileSecretKind.of(fileName)` and the generated folders set) so detection and the picker can't drift apart.
- `providedByRepository` comes from the repository part of provisioning: the untracked files matched by `.shogunai.json`'s copy and link patterns and `.worktreeinclude`. A folder is provided when every file in it is.
- A Git failure throws `WorktreeError.GitCommandFailed` like detection does, and the picker shows its message.

The desktop app receives it as an injected `listLocalFileCandidates: (suspend (ProjectConfig) -> List<LocalFileCandidate>)?` in `ProjectConfigViewModel`, wired in `AppContainer` next to `detectMobileSecrets`, so view model and UI tests pass a fake.

### Picker state separate from the dialog

`LocalFilePickerState` (in `ui/projectconfig`) holds the candidates load (loading, failed with message, loaded), the checked set, the filter and the pattern to offer; it is created by the view model from the current entries when the user activates "Choose…". Applying returns the paths to add and to remove and the pattern, and the view model applies them with its existing `addSecretFile`/`removeSecretFile`, which already normalize folders, refuse absolute patterns and keep link modes. The dialog composable (`LocalFilePickerDialog`) uses `ShogunDialog` with `DialogSize.Large`, a `Field` for the filter and a `LazyColumn` of rows with `Checkbox`, a folder or file icon, the path in the data typeface and a suggested or provided label, so it reads like the rest of the app.

Filtering by a pattern uses a small glob matcher following the local files' semantics: a leading `/` anchors to the repository root, a pattern with `/` matches the whole path, otherwise the name; `*` doesn't cross `/`, `**` does, plus `?` and `[...]`. It only narrows the visible list; what the pattern provisions is still decided by Git at creation time.

## Risks / Trade-offs

- [Windows and Linux can't be tried on the development Mac] → FileKit's platform code is the library's own and covered by its tests; the fallback to `JFileChooser` keeps folder choosing working if the native path fails; the release smoke test on those platforms (roadmap) will exercise it.
- [ProGuard strips classes reached by reflection in JNA or dbus-java, which only fails in the release build] → the release distributable is run locally on macOS and the folder dialog opened from it before the change is finished.
- [Large ignored trees make the list long] → `--directory` collapses ignored folders and generated folders are dropped, so the list is bounded by the repository's top-level ignored entries plus loose files.
- [Keystores detected only through a `storeFile` reference with an unusual name aren't marked suggested] → they are still listed; the settings' mobile secrets suggestions keep showing them.
- [A file inside an ignored folder can't be chosen alone] → the folder can be chosen whole, or the file typed or matched with a pattern from the picker's filter.
- [New dependencies (JNA, dbus-java, kotlinx-io) grow the app] → about 3 MB, accepted for a native dialog on every platform.

## Migration Plan

No data changes: local files keep their format and modes. Rollback is reverting the change; the `PathPicker` port keeps the end-to-end tests independent of the implementation.
