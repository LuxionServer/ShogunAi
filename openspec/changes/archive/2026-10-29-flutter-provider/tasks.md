# Tasks

## 1. Nested Gradle builds

- [x] 1.1 Add a shared helper that returns the repository's Gradle build folders (the main folder when it has `gradlew` or a settings file, else the direct subfolders with `settings.gradle(.kts)`, skipping hidden folders, `build`, `node_modules` and `Pods`), with tests for the root build, `android/`, and a root build next to `build-logic/`
- [x] 1.2 Make `CheckGradleBuildCacheUseCase` and `effectiveGradleJvmArgs` read the nested build's `gradle.properties` after the root's, and verify the `gradle-provider` delta scenarios (cache in `android/gradle.properties`, `-Xmx8G -XX:MaxMetaspaceSize=4G` becoming `-Xmx2g -XX:MaxMetaspaceSize=4G`) with unit tests
- [x] 1.3 Make `LocalAndroidSdk` look for `sdk.dir` in the nested builds' `local.properties` after the root's, and include them in the "places checked" error; verify with a test for the "SDK from a nested build" scenario
- [x] 1.4 Update the Gradle and Android emulator sections of `docs/guide/providers.md` and `providers.es.md` to say a Gradle build in `android/` is found too, and verify `mkdocs build --strict` passes

## 2. Flutter stack and mobile secrets

- [x] 2.1 Add `Stack.FLUTTER` and `SuggestedProvider.FLUTTER("flutter")` with the mapping Flutter → `flutter`, and make `DetectRepositoryStacksUseCase` detect it from the main folder's `pubspec.yaml` containing `sdk: flutter`; verify with tests for the Flutter app and Dart package scenarios
- [x] 2.2 Make detection count a Gradle build in a direct subfolder (using 1.1) and look for `com.android.` in `settings.gradle(.kts)`; verify with a test on the template layout (`android/settings.gradle.kts`, `android/build.gradle.kts`, `android/app/build.gradle.kts`, `ios/Runner.xcodeproj`) expecting Flutter, Gradle, Android and Xcode, and that the existing detection tests still pass
- [x] 2.3 Add `firebase_options.dart` (Firebase configuration) and the `.dart_tool` exclusion to `DetectMobileSecretsUseCase`, and drop any `local.properties` that sets `flutter.sdk` while still reading its keystore keys; verify with tests for the FlutterFire and Flutter local properties scenarios
- [x] 2.4 Make `shogun stacks` (text and JSON) list the Flutter stack and the `flutter` suggestion, and verify with a CLI test that runs it on a temporary repository with the template layout
- [x] 2.5 Add Flutter to the detection table and its evidence in `docs/guide/providers.md` and `providers.es.md`, and verify `mkdocs build --strict` passes

## 3. Flutter provider

- [x] 3.1 Read and write `providers.flutter` in the `.shogunai.json` reader and writer (`RepositoryProviders.flutter`), keeping unknown options; verify with tests for the `repository-provisioning` delta scenarios and that writing other providers keeps it
- [x] 3.2 Add a use case that runs `flutter pub get`, or `fvm flutter pub get` when `.fvmrc` exists, as argv in the worktree, with no timeout and cancellable, returning resolved or needed with the reason (not found, or exit code and the last lines of stderr); verify with fake-executor tests for success, FVM, command not found, non-zero exit and cancellation
- [x] 3.3 Call it from every creation flow and from provisioning, after the pods and before the post-creation commands, independent of the repository's approval and never rolling back the worktree; verify with use case tests for the order, the unapproved hook and the failure that keeps the worktree
- [x] 3.4 Report the outcome in `shogun new`, `shogun provision` and `shogun hook worktree-create` as a text line and as `flutter.pubGet` in JSON, absent when the provider isn't declared; verify with CLI tests for each output
- [x] 3.5 Add the Flutter workflow rule (condition: Flutter provider; device lines only for the declared device providers; `fvm flutter` with `.fvmrc`) to `WorkflowRules.kt` for `SessionStart` and `shogun guide resources`; verify with tests for the Android and iOS, only Android and FVM scenarios
- [x] 3.6 Write the Flutter section of `docs/guide/providers.md` and `providers.es.md`: what `flutter pub get` regenerates, the providers to combine for `android/` and `ios/`, devices with `-d`, a fixed web port with `env` and `${port}`, CocoaPods and Flutter's own `pod install`, and FVM; verify `mkdocs build --strict` passes

## 4. Desktop switch (after the UI redesign is merged into develop)

- [x] 4.1 Rebase on `develop` once the UI redesign is merged, then add the Flutter label, the suggestion's "Turn on" branch and the switch in "Repository configuration" (English and Spanish strings), carry `flutter` in `RepositoryConfigDraft` so saving keeps it, and pass `ResolveFlutterPackagesUseCase` to the desktop's `PrepareWorktreeEnvironmentUseCase` in `AppContainer.kt` so worktrees created from the app run `flutter pub get`; verify with the project configuration view model tests for the `project-configuration` delta scenarios and by checking the section with `shogun headless start`

## 5. Integration

- [x] 5.1 Run `./gradlew test` with every compiler warning as an error, and verify on the scratch sample layout (`pubspec.yaml`, `android/`, `ios/`, ignored `android/local.properties` with `flutter.sdk`, `lib/firebase_options.dart`) that `shogun stacks` and the mobile secrets suggestions match the specs
- [ ] 5.2 With Flutter installed (only after Ciro agrees), create a project with `flutter create`, declare `flutter`, `gradle`, `emulator`, `ios-simulator` and `xcode`, create a worktree with `shogun new` and check that `flutter build apk --debug` and `flutter build ios --simulator` succeed in it without manual steps

## Workflow follow-up

- Update `docs/roadmap.md` and `docs/roadmap.es.md` when archiving: remove the Flutter item and add the follow-ups (the pub cache in the shared caches view, finding a Flutter SDK outside `PATH` from the desktop app, Dart mono-repositories with pub workspaces or Melos).
- Archive with `/opsx:archive` before opening the pull request, with `--base develop` and `--assignee CiroFeliu`.
