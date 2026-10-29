# Proposal

## Why

A fresh worktree of a Flutter repository doesn't build: `.dart_tool/`, `android/local.properties` (with the `flutter.sdk` that `android/settings.gradle.kts` requires), `ios/Flutter/Generated.xcconfig` and `ios/.symlinks` are all ignored by Git and only appear after `flutter pub get`. ShogunAi doesn't recognise the stack either: on a project made from Flutter's template, `shogun stacks` only finds Xcode, because the Gradle build lives in `android/` and its Android plugin is declared in `android/settings.gradle.kts` and `android/app/build.gradle.kts`, where detection doesn't look. Once found, the Android and iOS providers already do most of the work; what's missing is the Flutter step itself, a few places that assume the Gradle build is at the repository root, and telling agents how to aim `flutter run` at their worktree's device.

## What Changes

- New `flutter` provider, declared with `"providers": { "flutter": {} }`. Creating or provisioning a worktree runs `flutter pub get` in it (`fvm flutter pub get` when the repository has `.fvmrc`), after the files and pods and before the post-creation commands. It doesn't wait for the repository's approval, since the command is fixed and runs no repository code. A failure never fails the worktree: the result says `flutter pub get` is still needed and why.
- `shogun new`, `shogun provision` and the worktree creation hook report the outcome of `flutter pub get` in text and JSON.
- The `SessionStart` context of a Flutter repository tells agents to pass the worktree's device explicitly (`flutter run -d "$ANDROID_SERIAL"` or `-d "$IOS_SIMULATOR_UDID"`), because Flutter ignores `ANDROID_SERIAL` and other worktrees' devices are online too. It also tells them not to launch emulators with `flutter emulators --launch`.
- Stack detection recognises Flutter (a `pubspec.yaml` in the main folder that depends on the Flutter SDK) and suggests the `flutter` provider. It also recognises a Gradle build in a direct subfolder (`android/settings.gradle.kts`) and the Android plugin declared in a `settings.gradle(.kts)`, so a Flutter project also gets the `gradle` and `emulator` suggestions it lacks today. React Native's `android/` folder benefits in the same way.
- The Gradle provider reads `gradle.properties` from that nested Gradle build when the repository root has none, both for the build cache notice and for the JVM arguments kept when it sets the daemon heap. The emulator provider also looks for `sdk.dir` in that build's `local.properties`.
- Mobile secret detection reports FlutterFire's ignored `firebase_options.dart`, and stops suggesting a link for a `local.properties` that Flutter writes (`flutter.sdk`), because Flutter rewrites it in every worktree and a link would write into the base repository's file.
- The project settings get a Flutter switch, and the stack suggestion can turn it on.

## Capabilities

### New Capabilities

- `flutter-provider`: the opt-in Flutter provider: `flutter pub get` when a worktree is created or provisioned, its report, and the agent context for running Flutter on the worktree's devices.

### Modified Capabilities

- `stack-detection`: the Flutter stack, Gradle builds in a direct subfolder, the Android plugin in settings files, the `flutter` suggestion, `firebase_options.dart` as a mobile secret, and Flutter's own `local.properties` left out.
- `gradle-provider`: the build cache notice and the daemon's JVM arguments also read the nested Gradle build's `gradle.properties`.
- `emulator-provider`: the Android SDK is also looked up in the nested Gradle build's `local.properties`.
- `repository-provisioning`: `providers.flutter` is accepted in `.shogunai.json`.
- `project-configuration`: the Flutter switch in "Repository configuration".

## Impact

- Core: `DetectRepositoryStacksUseCase`, `StackDetection.kt` (new `Stack.FLUTTER` and `SuggestedProvider.FLUTTER`), `DetectMobileSecretsUseCase`, `CheckGradleBuildCacheUseCase`, `GradleSettingsContribution.kt`, `LocalAndroidSdk`, the `.shogunai.json` reader and writer, a new use case for `flutter pub get` called from worktree creation and provisioning next to `PrepareCocoaPodsUseCase`, and a new rule in `WorkflowRules.kt`.
- CLI: `shogun stacks`, `shogun new`, `shogun provision` and `shogun hook worktree-create`, text and JSON.
- Desktop app: the Flutter switch and the suggestion label in `RepositoryConfigSection.kt` and `ProjectConfigViewModel.kt`, applied only after the UI redesign currently in progress is merged.
- Docs: a Flutter section and the detection table in the providers guide (English and Spanish), and the roadmap.
- No new dependency. Flutter isn't needed to build or test ShogunAi; the provider's tests use a fake command executor and a hand-made sample layout.
