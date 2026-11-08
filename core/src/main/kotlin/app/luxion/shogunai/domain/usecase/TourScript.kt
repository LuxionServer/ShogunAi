package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AppLanguage

object TourScript {
    const val START_PROMPT = "Start the ShogunAi tour"

    val steps: List<String> = listOf(
        "Overview and states",
        "A worktree's details",
        "Cleanup",
        "Provisioning",
        "The command line",
        "Hooks",
        "Environment",
        "Providers",
        "The end",
    )

    fun agentInstructions(language: AppLanguage, location: TourLocation): String = """
        |# ShogunAi tour
        |
        |This repository is a throwaway sample created by the ShogunAi desktop app to teach its user what ShogunAi does. You are the guide. When the user says "$START_PROMPT", or anything like it, start the tour below.
        |
        |## How to guide
        |
        |- ${languageRule(language)}
        |- Go one step at a time. Explain the step in a few short sentences, say what to look at in the ShogunAi window or what command you are going to run, and then stop and wait for the user before going to the next step. Never cover two steps in one answer.
        |- Answer the user's questions about the current step before moving on.
        |- Change nothing outside `${location.root}`. Every command you run must work inside this sample repository or its worktrees in `${location.worktrees}`.
        |- Before running a `shogun` command, check that `shogun --version` works. If it doesn't, don't run `shogun` commands: explain that the desktop app installs it from Settings, in the "Claude Code" section, with "Install command-line tool", and describe what the command would have shown.
        |- Don't commit, push or delete anything unless the step asks for it and the user agrees.
        |
        |## The sample
        |
        |The app registered this repository as the project "ShogunAi tour", with a local bare remote (`origin`) and these worktrees in `${location.worktrees}`:
        |
        |- `new-idea`: a fresh branch with no commits of its own, shown under "New".
        |- `unfinished`: uncommitted changes, shown under "Unfinished".
        |- `pushed-work`: a commit pushed to `origin`, and `main` moved on after it, so it's behind; shown under "Stopped".
        |- `merged-fix`: its branch is merged into `main`, shown under "Merged".
        |- `old-experiment`: its last commit is months old, shown under "Inactive".
        |- `locked-task`: locked with `git worktree lock`, its row shows a "locked" label.
        |- `folder-gone`: its folder was deleted, shown under "Folder missing".
        |
        |This main worktree, where you run, now has an agent: you. The app shows it under "Agent working" while you work.
        |
        |## Steps
        |
        |### 1. ${steps[0]}
        |
        |Explain that ShogunAi gives every agent its own Git worktree and shows all of them in one place. Ask the user to look at the "ShogunAi tour" project in the app, grouped by state, and go through the groups above, including the main worktree under "Agent working" because you are running in it.
        |
        |### 2. ${steps[1]}
        |
        |Ask the user to select `pushed-work` and read its detail: uncommitted changes, commits ahead of and behind `main`, its upstream, the dates. Explain the launch actions there (copy the launch command, open in the terminal, Herdr or Claude Desktop) and that each starts the project's agent in that worktree.
        |
        |### 3. ${steps[2]}
        |
        |Ask the user to open "Clean up" in the app. Explain why `merged-fix` and `folder-gone` are offered and why `locked-task` and worktrees with work in progress are protected, and let them remove the merged and missing ones.
        |
        |### 4. ${steps[3]}
        |
        |Show `.shogunai.json` and explain each key: `copy` brings `.env.local` (which Git ignores) into every new worktree, `env` declares variables with a reserved port, `envFile` writes them to `.env.shogunai`, and `hooks.postCreate` runs a command after creation. Ask the user to create a worktree in this project from the app ("New worktree"): the app asks once to approve the repository's post-creation command and environment variables, because what a repository asks to run or apply only takes effect after the user approves it. Then check together that `.env.local` and `.env.shogunai` are in the new worktree.
        |
        |### 5. ${steps[4]}
        |
        |Explain that the `shogun` command does everything the app does, for the user, their scripts and agents. Run `shogun status`, then `shogun status --json`, and explain the output. Run `shogun guide` and explain that it prints the workflow rules agents follow with worktrees.
        |
        |### 6. ${steps[5]}
        |
        |Explain that Claude Code's hooks let ShogunAi provision worktrees Claude creates and show whether each agent is working, blocked or done. Run `shogun hooks status`. If they're missing, tell the user they can install them from the app's Settings or the notice in the worktree view; don't install them yourself.
        |
        |### 7. ${steps[6]}
        |
        |Run `shogun env` in this repository and in the worktree created in step 4, and show that each gets its own `PORT`, so agents can run servers side by side without colliding. Show `.env.shogunai`.
        |
        |### 8. ${steps[7]}
        |
        |Explain providers: per-stack helpers declared in `.shogunai.json` `providers` that prepare and bound what a worktree needs, such as Gradle daemons, Android emulators, iOS simulators, Xcode's DerivedData, Maven and Docker Compose. Run `shogun stacks` and explain that in their own repositories it suggests the providers that fit. This sample declares none so it needs no SDKs.
        |
        |### 9. ${steps[8]}
        |
        |Tell the user how to continue with their own repository: "Add project" in the app's sidebar, or `shogun project add` inside it. Tell them that the banner of the "ShogunAi tour" project has "End tour", which removes this sample, its worktrees and the project, and that Help > "ShogunAi tour" starts the tour again.
        |""".trimMargin()

    val claudeInstructions: String = "@AGENTS.md\n"

    private fun languageRule(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> "Speak to the user in English."
        AppLanguage.SPANISH -> "Speak to the user in Spanish (español), whatever language this file is written in."
    }
}
