# Proposal

## Why

A fresh worktree of a CocoaPods project doesn't build: its `Pods/` folder is ignored by Git, so the agent has to run `pod install` (Ruby, the specs CDN, minutes) before the first build, and every worktree keeps a full copy of the pods. Swift packages are already in a better place, since their checkouts live in each worktree's DerivedData under the quota and Xcode shares its repository cache, but nobody can see how much of the DerivedData they take nor how large the shared package caches have grown.

## What Changes

- New option `cocoaPods` of the Xcode provider in `.shogunai.json`. When it's on, creating or provisioning a worktree on macOS clones the base repository's `Pods/` next to every committed `Podfile.lock` with APFS copy-on-write (`cp -c`), only when the base's `Pods/Manifest.lock` matches the worktree's `Podfile.lock`. The clone is instant and takes no extra disk until files change. When they don't match, or the clone isn't possible, nothing is copied and the result says that `pod install` is needed there.
- The result of `shogun new`, `shogun provision` and the worktree creation hook reports the pods cloned and the folders that need `pod install`, in text and JSON.
- The Xcode provider measures how much of each DerivedData folder are Swift packages (`SourcePackages`), remembered with the folder's size, and shows it in `shogun xcode` (text and JSON) and in the desktop app's DerivedData view. Checkouts are not shared between worktrees.
- The shared package caches, SwiftPM's (`~/Library/Caches/org.swift.swiftpm`) and CocoaPods' (`~/Library/Caches/CocoaPods`), are shown apart, like the compilation cache: they don't count against the DerivedData quota and nothing deletes them.
- The project settings show the new option as a switch of the Xcode provider.

## Capabilities

### New Capabilities

- `cocoapods-provisioning`: cloning the base repository's CocoaPods pods into a new or provisioned worktree when its lock matches, and reporting where `pod install` is still needed.

### Modified Capabilities

- `xcode-provider`: DerivedData folders report the size of their Swift packages, remembered with their size, and the shared SwiftPM and CocoaPods caches are shown apart from the quota.
- `repository-provisioning`: `providers.xcode` accepts the optional boolean `cocoaPods`.
- `project-configuration`: the Xcode provider's options in the project settings include CocoaPods.

## Impact

- Core: `XcodeProvider` and the `.shogunai.json` reader and writer; a new use case to clone pods called from worktree creation and provisioning; `DerivedDataSource`, the measured and remembered sizes and the DerivedData listing; a source for the shared package caches.
- CLI: `shogun new`, `shogun provision`, the worktree creation hook and `shogun xcode`, text and JSON.
- Desktop app: the Xcode switch's options in the repository configuration section, the DerivedData view and the machine resources holder that measures it. `ProjectWorktrees.kt` and `ProjectSettingsView.kt` are left untouched.
- Docs: the Xcode provider pages (English and Spanish) and the roadmap.
