package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.CustomPoolCommandKind
import app.luxion.shogunai.domain.model.CustomPoolProblem

internal fun englishCustomPoolTexts(problem: CustomPoolProblem): ErrorTexts = when (problem) {
    is CustomPoolProblem.UnknownPool -> ErrorTexts(
        "The repository declares no pool \"${problem.pool}\".",
        if (problem.declared.isEmpty()) "Declare it under \"pools\" in .shogunai.json." else "Use one of its pools: ${problem.declared.joinToString()}.",
    )
    is CustomPoolProblem.NotApproved -> ErrorTexts(
        "The commands of pool \"${problem.pool}\" aren't approved for ${problem.repositoryPath}.",
        "Review and approve the repository's configuration in project settings or with shogun trust.",
    )
    is CustomPoolProblem.Full -> ErrorTexts(
        "Pool \"${problem.pool}\" is full: ${problem.holders.size} of ${problem.max} slots in use.",
        "Wait for a slot with shogun pool acquire ${problem.pool} --wait, or release one from the worktree that holds it.",
    )
    is CustomPoolProblem.WaitTimedOut -> ErrorTexts(
        "No slot of pool \"${problem.blocked.pool}\" was free within ${problem.waited.toMinutes()} min.",
        "Release one from the worktree that holds it, or try again later.",
    )
    is CustomPoolProblem.CommandFailed -> ErrorTexts(
        "The ${if (problem.kind == CustomPoolCommandKind.ACQUIRE) "acquire" else "release"} command of pool \"${problem.pool}\" failed.",
        "Read the technical details and fix the command declared in .shogunai.json or its cause.",
    )
}

internal fun spanishCustomPoolTexts(problem: CustomPoolProblem): ErrorTexts = when (problem) {
    is CustomPoolProblem.UnknownPool -> ErrorTexts(
        "El repositorio no declara ningún pool \"${problem.pool}\".",
        if (problem.declared.isEmpty()) "Decláralo en \"pools\" de .shogunai.json." else "Usa uno de sus pools: ${problem.declared.joinToString()}.",
    )
    is CustomPoolProblem.NotApproved -> ErrorTexts(
        "Los comandos del pool \"${problem.pool}\" no están aprobados para ${problem.repositoryPath}.",
        "Revisa y aprueba la configuración del repositorio en los ajustes del proyecto o con shogun trust.",
    )
    is CustomPoolProblem.Full -> ErrorTexts(
        "El pool \"${problem.pool}\" está lleno: ${problem.holders.size} de ${problem.max} plazas en uso.",
        "Espera una plaza con shogun pool acquire ${problem.pool} --wait, o libera una desde el worktree que la tiene.",
    )
    is CustomPoolProblem.WaitTimedOut -> ErrorTexts(
        "Ninguna plaza del pool \"${problem.blocked.pool}\" quedó libre en ${problem.waited.toMinutes()} min.",
        "Libera una desde el worktree que la tiene, o inténtalo más tarde.",
    )
    is CustomPoolProblem.CommandFailed -> ErrorTexts(
        "Falló el comando ${if (problem.kind == CustomPoolCommandKind.ACQUIRE) "acquire" else "release"} del pool \"${problem.pool}\".",
        "Lee los detalles técnicos y corrige el comando declarado en .shogunai.json o su causa.",
    )
}

internal fun customPoolDetails(problem: CustomPoolProblem): TechnicalDetails? =
    (problem as? CustomPoolProblem.CommandFailed)?.let { TechnicalDetails(it.command, it.exitCode, it.errorOutput) }
