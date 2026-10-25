# Tasks

## 1. Version bump

- [ ] 1.1 Change `composeMultiplatform` in `gradle/libs.versions.toml` from `1.11.1` to `1.12.2`, leaving Kotlin, lifecycle and `materialIconsCore` unchanged, and verify `./gradlew :desktopApp:dependencies --configuration runtimeClasspath` resolves Compose runtime 1.12.x and lifecycle 2.11.0 without failures
- [ ] 1.2 Run `./gradlew test --rerun-tasks --warning-mode all` and verify it passes with no Kotlin warnings and no Gradle deprecation messages; migrate any deprecated call site to its replacement (never `@Suppress`) until it does

## 2. App validation

- [ ] 2.1 Run `./gradlew :desktopApp:run` and verify the app starts and the project list, a worktree's detail, settings and the tray menu render and respond as before
- [ ] 2.2 Run `./gradlew :desktopApp:hotRun --auto`, edit a composable's text and verify the running app reloads it; revert the edit
- [ ] 2.3 With `hotRun` running, start `./gradlew :desktopApp:hotMcpServer` and verify it starts and connects to the running app
- [ ] 2.4 Build `./gradlew :desktopApp:createReleaseDistributable` and launch the result, verifying ProGuard completes without new warnings and the app opens; add keep rules to `desktopApp/proguard-rules.pro` only if it does not

## Workflow follow-up

- Commit the implementation once every task is validated.
- Archive the change (`/opsx:archive`) before opening the pull request; `docs/roadmap.md` and `docs/roadmap.es.md` don't mention this upgrade, so no roadmap change is expected.
- Open the pull request against `develop`; it must be merged before `background-app-control` is implemented.
