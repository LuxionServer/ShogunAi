## 1. Gradle checks read the project's Gradle build

- [ ] 1.1 Add tests to `CheckGradleBuildJavaTest` and `CheckProviderToolsUseCaseTest` for each scenario of "Find the project's Gradle build": a build in `android/` (wrapper, version and Java check), the nested build's daemon criteria and Android Gradle Plugin, the root winning over a subfolder, the first nested build in name order, and no build anywhere; see them fail
- [ ] 1.2 In `CheckProviderToolsUseCase`, resolve the Gradle build folder with `gradleBuildFolders` (first folder, else the repository) and use it for the Gradle wrapper lookup and the Gradle build's Java checks
- [ ] 1.3 Run `./gradlew test koverVerify` and fix any coverage gap

## 2. Roadmap and wrap-up

- [ ] 2.1 Remove the bullet about Gradle checks for a build in a subfolder from `docs/roadmap.md` and `docs/roadmap.es.md`
- [ ] 2.2 Bring in `origin/develop`, re-run `./gradlew test koverVerify`, archive the change and open the pull request against `develop`
