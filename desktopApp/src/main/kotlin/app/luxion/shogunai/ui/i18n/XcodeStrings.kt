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
    val simulatorStorageTitle: String
    val simulatorStorageUnavailable: String
    val noSimulatorRuntimes: String
    fun simulatorRuntimesSummary(count: Int, size: String): String
    val runtimeUnused: String
    val runtimeBundled: String
    val runtimeBooted: String
    val runtimeDefault: String
    fun runtimeUsedBy(users: List<String>): String
    val runtimeNeverUsed: String
    val sizeUnknown: String
    val deleteRuntime: String
    val confirmDeleteRuntimeTitle: String
    fun confirmDeleteRuntime(name: String, size: String): String
    fun runtimeDeleted(name: String, freed: String, unavailable: Int): String
    fun unavailableDevices(count: Int, size: String): String
    val deleteUnavailableDevices: String
    val confirmDeleteUnavailableDevicesTitle: String
    fun confirmDeleteUnavailableDevices(count: Int, size: String): String
    fun unavailableDevicesDeleted(count: Int, freed: String): String
    fun dyldCachesSummary(size: String): String
    val dyldCacheCurrent: String
    val dyldCacheStale: String
    val copyRemovalCommand: String
}
