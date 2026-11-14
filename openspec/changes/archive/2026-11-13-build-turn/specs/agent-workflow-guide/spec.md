## ADDED Requirements

### Requirement: Rules for the build turn
The `resources` topic SHALL tell agents, in every repository and on every platform, to run heavy builds and tests (Gradle, Maven, `xcodebuild`, `swift build` and `swift test`) through `shogun build run -- <command>`, in the background or with a long enough shell timeout since it can wait up to 30 minutes for a slot. The rule SHALL NOT be in the session-start context, since the build-turn hook tells the agent when it applies.

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in any repository
- **THEN** the output says to run heavy builds through `shogun build run --`

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree of a Gradle repository
- **THEN** the context doesn't include the build-turn rule
