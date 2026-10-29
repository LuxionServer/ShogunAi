package app.luxion.shogunai.infrastructure

import java.nio.file.Path

class ShogunFiles(home: String) {
    val folder: Path = Path.of(home, ".shogunai")
    val projects: Path = folder.resolve("projects.json")
    val settings: Path = folder.resolve("settings.json")
    val trust: Path = folder.resolve("trust.json")
    val multirepos: Path = folder.resolve("multirepos.json")
    val composeProjects: Path = folder.resolve("compose-projects.json")
    val orcaWorkspaces: Path = folder.resolve("orca.json")
    val derivedDataSizes: Path = folder.resolve("derived-data-sizes.json")
    val agentSessions: Path = folder.resolve("agent-sessions")
    val appRequests: Path = folder.resolve("app-requests")
    val appLock: Path = folder.resolve("app.lock")
    val logs: Path = folder.resolve("logs")
    val headless: Path = folder.resolve("headless")
}
