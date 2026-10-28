# cocoapods-provisioning Specification

## Purpose
Gives a new or provisioned worktree of a CocoaPods project the pods it needs to build, cloned from the base repository when they match its lock, and says where `pod install` is still needed.

## Requirements

### Requirement: Pods only when declared on macOS
The system SHALL clone pods only when the repository declares the Xcode provider with `"cocoaPods": true` and ShogunAi runs on macOS. Otherwise creating and provisioning a worktree SHALL NOT read, clone or report pods.

#### Scenario: Option not declared
- **WHEN** the repository declares `{ "xcode": {} }` and a worktree is created
- **THEN** no `Pods/` folder is cloned and the result reports no pods

#### Scenario: Other operating system
- **WHEN** the repository declares `{ "xcode": { "cocoaPods": true } }` and ShogunAi runs on Linux
- **THEN** the option is ignored without an error

### Requirement: Find the pod folders of a worktree
The pod folders SHALL be the folders of the new worktree that contain a `Podfile.lock` tracked by Git, at any depth. A `Podfile.lock` that isn't tracked SHALL be ignored.

#### Scenario: Lock in a subfolder
- **WHEN** the repository tracks `iosApp/Podfile.lock`
- **THEN** `iosApp` is a pod folder of the worktree

#### Scenario: Untracked lock
- **WHEN** `Podfile.lock` is ignored by Git
- **THEN** no pod folder is found and nothing is cloned or reported

### Requirement: Clone matching pods from the base repository
For each pod folder whose `Pods` doesn't exist in the worktree, the system SHALL clone the base repository's `Pods` into it with copy-on-write when the base's `Pods/Manifest.lock` has the same content as the worktree's `Podfile.lock`. It SHALL never fall back to a full copy.

#### Scenario: Lock matches
- **WHEN** the worktree's `iosApp/Podfile.lock` equals the base's `iosApp/Pods/Manifest.lock`
- **THEN** the worktree gets `iosApp/Pods` cloned from the base and the result reports it as cloned

#### Scenario: Pods already present
- **WHEN** the worktree already has `iosApp/Pods`, for example on a second provisioning
- **THEN** it is left unchanged and not reported

### Requirement: Report where pod install is needed
When a pod folder has no `Pods` and they can't be cloned, because the base has no `Pods/Manifest.lock`, it differs from the worktree's `Podfile.lock`, or the clone fails, the system SHALL leave no partial `Pods` and SHALL report the folder as needing `pod install`, with the reason.

#### Scenario: Base out of date
- **WHEN** the worktree's branch changed `Podfile.lock` and the base's `Pods/Manifest.lock` is older
- **THEN** nothing is cloned and the result says `iosApp` needs `pod install` because the base's pods differ

#### Scenario: Base without pods
- **WHEN** the base repository never ran `pod install`
- **THEN** the result says the folder needs `pod install` because the base has no pods

#### Scenario: Clone not possible
- **WHEN** the base is on a volume without copy-on-write and the clone fails
- **THEN** the partial `Pods` is removed and the result says the folder needs `pod install` because the clone failed

### Requirement: Pods never fail the worktree
Cloning pods SHALL happen in every creation flow and when provisioning an existing worktree, after the files are copied and before the post-creation commands run. A failure to clone or report pods SHALL NOT fail or roll back the worktree.

#### Scenario: Post-creation command after the clone
- **WHEN** the repository declares `pod install` as a post-creation command and the pods were cloned
- **THEN** `pod install` runs with the cloned `Pods` already in place

#### Scenario: Unexpected error
- **WHEN** listing the tracked locks fails
- **THEN** the worktree is created and the result reports that the pods couldn't be prepared

### Requirement: Pods in the command results
`shogun new`, `shogun provision` and the worktree creation hook SHALL report the pod folders cloned and those that need `pod install` with their reason, as lines in text and as `pods.cloned` and `pods.installNeeded` in JSON.

#### Scenario: JSON output
- **WHEN** `shogun provision --json` cloned `iosApp/Pods` and `macApp` needs `pod install`
- **THEN** the JSON has `pods.cloned` `["iosApp"]` and `pods.installNeeded` with `macApp` and its reason
