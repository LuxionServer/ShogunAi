package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.portKeyLabel

fun portEntries(ports: Map<String, Int>): List<String> =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .map { (key, port) -> "${portKeyLabel(key)} $port" }

fun portsLabel(ports: Map<String, Int>): String = portEntries(ports).joinToString(" · ")
