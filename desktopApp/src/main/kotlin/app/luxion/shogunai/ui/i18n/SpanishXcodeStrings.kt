package app.luxion.shogunai.ui.i18n

object SpanishXcodeStrings : XcodeStrings {
    override val xcodeDerivedDataTitle = "DerivedData de Xcode"
    override val xcodeDerivedDataUnavailable = "No se pueden inspeccionar los DerivedData en este sistema"
    override fun derivedDataSummary(count: Int, totalSize: String) =
        (if (count == 1) "1 carpeta" else "$count carpetas") + " · $totalSize"
    override fun derivedDataOrphans(count: Int, size: String) =
        if (count == 1) "1 carpeta huérfana · $size" else "$count carpetas huérfanas · $size"
    override fun derivedDataOnUnavailableVolumes(count: Int) =
        if (count == 1) "1 carpeta está en un volumen no montado y se conserva"
        else "$count carpetas están en volúmenes no montados y se conservan"
    override fun derivedDataCompilationCache(size: String) = "Caché de compilación · $size"
    override fun derivedDataSwiftPmCache(size: String) = "Caché de SwiftPM · $size"
    override fun derivedDataCocoaPodsCache(size: String) = "Caché de CocoaPods · $size"
    override fun derivedDataWithPackages(name: String, size: String) = "$name · paquetes $size"
    override fun derivedDataQuotaUsage(usage: String, quota: String, automatic: Boolean) =
        "$usage de $quota de cuota" + if (automatic) " (automática)" else ""
    override val derivedDataQuotaUnknown = "Cuota automática desconocida: no se pudo leer el espacio libre en disco"
    override fun derivedDataQuotaExceeded(inUse: List<String>) =
        "Por encima de la cuota" + if (inUse.isEmpty()) "" else "; se conservan por estar en uso: ${inUse.joinToString(", ")}"
    override fun derivedDataReleased(count: Int, freed: String) =
        "Se ${if (count == 1) "liberó 1 carpeta" else "liberaron $count carpetas"} para cumplir la cuota, liberando $freed"
    override fun derivedDataDeleted(count: Int, freed: String) =
        if (count == 1) "1 carpeta de DerivedData borrada · $freed liberados"
        else "$count carpetas de DerivedData borradas · $freed liberados"
    override fun swiftBuildFolder(name: String) = "$name · .build"
    override fun swiftBuildsSummary(count: Int, size: String) =
        (if (count == 1) "1 carpeta .build" else "$count carpetas .build") + " · $size"
    override val deleteUnusedSwiftBuilds = "Borrar .build sin uso"
    override val confirmDeleteUnusedSwiftBuildsTitle = "¿Borrar las carpetas .build sin uso?"
    override fun confirmDeleteUnusedSwiftBuilds(count: Int, size: String?): String {
        val total = size?.let { " ($it en total)" }.orEmpty()
        return "Se borrarán las carpetas .build de los worktrees que no estén en uso, de $count$total. " +
            "Los worktrees cuyo agente trabaja o que se compilaron en la última hora conservan la suya; el siguiente swift build la vuelve a crear."
    }
    override fun swiftBuildsDeleted(count: Int, freed: String, inUse: Int) =
        (if (count == 1) "1 carpeta .build borrada · $freed liberados" else "$count carpetas .build borradas · $freed liberados") +
            if (inUse == 0) "" else " · $inUse conservadas por estar en uso"
    override val swiftBuildDeleteBlockedDeleting = "Se están borrando carpetas .build"
}
