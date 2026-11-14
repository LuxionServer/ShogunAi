package app.luxion.shogunai.ui.i18n

interface XcodeStrings {
    val xcodeDerivedDataTitle: String
    val xcodeDerivedDataUnavailable: String
    fun derivedDataSummary(count: Int, totalSize: String): String
    fun derivedDataOrphans(count: Int, size: String): String
    fun derivedDataOnUnavailableVolumes(count: Int): String
    fun derivedDataCompilationCache(size: String): String
    fun derivedDataSwiftPmCache(size: String): String
    fun derivedDataCocoaPodsCache(size: String): String
    fun derivedDataWithPackages(name: String, size: String): String
    fun derivedDataDeleted(count: Int, freed: String): String
    fun derivedDataQuotaUsage(usage: String, quota: String, automatic: Boolean): String
    val derivedDataQuotaUnknown: String
    fun derivedDataQuotaExceeded(inUse: List<String>): String
    fun derivedDataReleased(count: Int, freed: String): String
    fun swiftBuildFolder(name: String): String
    fun swiftBuildsSummary(count: Int, size: String): String
    val deleteUnusedSwiftBuilds: String
    val confirmDeleteUnusedSwiftBuildsTitle: String
    fun confirmDeleteUnusedSwiftBuilds(count: Int, size: String?): String
    fun swiftBuildsDeleted(count: Int, freed: String, inUse: Int): String
    val swiftBuildDeleteBlockedDeleting: String
}
