## 1. Fix the quoting

- [ ] 1.1 In `CopyWorktreeLaunchCommandUseCase.invoke`, change the built command from `"cd '$worktreePath' && ..."` to `"cd \"$worktreePath\" && ..."` and verify the file compiles.

## 2. Update tests

- [ ] 2.1 In `CopyWorktreeLaunchCommandUseCaseTest`, update the expected clipboard strings in the "copies the cd and default headroom-wrapped command" and "omits the headroom wrap when disabled" tests to use double quotes, and verify `./gradlew :desktopApp:test --tests "*CopyWorktreeLaunchCommandUseCaseTest*"` passes.

## 3. Verify

- [ ] 3.1 Run `./gradlew test` and verify the full suite passes with no other test depending on the old single-quote format.
