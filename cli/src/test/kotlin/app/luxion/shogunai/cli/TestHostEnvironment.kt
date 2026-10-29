package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.infrastructure.SystemHostEnvironment

fun hostEnvironment(home: String = "/home/dev", variables: Map<String, String> = emptyMap()) =
    SystemHostEnvironment(HostOs.current(), variables, home, loginShell = null)
