package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.WorkflowCondition
import app.luxion.shogunai.domain.model.WorkflowRule
import app.luxion.shogunai.domain.model.WorkflowRules
import app.luxion.shogunai.domain.model.WorkflowSelection
import app.luxion.shogunai.domain.model.WorkflowTopic

internal fun guideText(selection: WorkflowSelection): List<String> = buildList {
    add(WorkflowRules.PRECEDENCE)
    addAll(rulesByTopic(selection.applicable) { it.text })
    if (selection.inapplicable.isNotEmpty()) {
        add("")
        add("Not applicable here:")
        selection.inapplicable.forEach { add("- ${it.text} (applies ${it.condition.label})") }
    }
}

internal fun plainGuideText(rules: List<WorkflowRule>): List<String> = buildList {
    add(WorkflowRules.PRECEDENCE)
    addAll(rulesByTopic(rules) { "${it.text} (${if (it.condition == WorkflowCondition.ALWAYS) "always" else "applies ${it.condition.label}"})" })
}

internal fun migrateText(file: String?): List<String> = buildList {
    add(WorkflowRules.PRECEDENCE)
    WorkflowTopic.entries.forEach { topic ->
        add("")
        add("## ${topic.id}")
        WorkflowRules.all.filter { it.topic == topic }.forEach { add("- ${it.id}: ${it.text}") }
    }
    add("")
    add("Instruction for the agent:")
    add("Read ${file ?: "the AGENTS.md the person points you to"} and compare it with the rules above by id.")
    add("List every line or paragraph that repeats one of them, say which rule it repeats, and propose removing it.")
    add("Keep anything that adds to a rule or is the person's own preference.")
    add("Show the diff of the proposed removals and apply nothing until the person confirms.")
}

private fun rulesByTopic(rules: List<WorkflowRule>, render: (WorkflowRule) -> String): List<String> =
    WorkflowTopic.entries.flatMap { topic ->
        val ofTopic = rules.filter { it.topic == topic }
        if (ofTopic.isEmpty()) emptyList() else listOf("", "## ${topic.id}") + ofTopic.map { "- ${render(it)}" }
    }.drop(1)
