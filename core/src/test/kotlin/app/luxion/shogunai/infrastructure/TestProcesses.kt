package app.luxion.shogunai.infrastructure

internal val isWindowsHost: Boolean = System.getProperty("os.name").startsWith("Windows")

internal fun echoCommand(text: String): List<String> = if (isWindowsHost) listOf("cmd", "/c", "echo", text) else listOf("echo", text)

internal fun startSleepingProcess(): Process =
    ProcessBuilder(if (isWindowsHost) listOf("ping", "-n", "30", "127.0.0.1") else listOf("sleep", "30")).start()
