package app.luxion.shogunai.ui.i18n

object EnglishXcodeStrings : XcodeStrings {
    override val xcodeDerivedDataTitle = "Xcode DerivedData"
    override val xcodeDerivedDataUnavailable = "DerivedData can't be inspected on this system"
    override fun derivedDataSummary(count: Int, totalSize: String) =
        (if (count == 1) "1 folder" else "$count folders") + " · $totalSize"
    override fun derivedDataOrphans(count: Int, size: String) =
        if (count == 1) "1 orphaned folder · $size" else "$count orphaned folders · $size"
    override fun derivedDataOnUnavailableVolumes(count: Int) =
        if (count == 1) "1 folder is on an unavailable volume and is kept"
        else "$count folders are on unavailable volumes and are kept"
    override fun derivedDataCompilationCache(size: String) = "Compilation cache · $size"
    override fun derivedDataSwiftPmCache(size: String) = "SwiftPM cache · $size"
    override fun derivedDataCocoaPodsCache(size: String) = "CocoaPods cache · $size"
    override fun derivedDataWithPackages(name: String, size: String) = "$name · packages $size"
    override fun derivedDataQuotaUsage(usage: String, quota: String, automatic: Boolean) =
        "$usage of $quota quota" + if (automatic) " (automatic)" else ""
    override val derivedDataQuotaUnknown = "Automatic quota unknown: the free disk space couldn't be read"
    override fun derivedDataQuotaExceeded(inUse: List<String>) =
        "Over the quota" + if (inUse.isEmpty()) "" else "; kept because they are in use: ${inUse.joinToString(", ")}"
    override fun derivedDataReleased(count: Int, freed: String) =
        "Released $count ${if (count == 1) "folder" else "folders"} to honour the quota, freeing $freed"
    override fun derivedDataDeleted(count: Int, freed: String) =
        if (count == 1) "1 DerivedData folder deleted · $freed freed" else "$count DerivedData folders deleted · $freed freed"
    override fun swiftBuildFolder(name: String) = "$name · .build"
    override fun swiftBuildsSummary(count: Int, size: String) =
        (if (count == 1) "1 .build folder" else "$count .build folders") + " · $size"
    override val deleteUnusedSwiftBuilds = "Delete unused .build"
    override val confirmDeleteUnusedSwiftBuildsTitle = "Delete unused .build folders?"
    override fun confirmDeleteUnusedSwiftBuilds(count: Int, size: String?): String {
        val total = size?.let { " ($it in all)" }.orEmpty()
        return "The .build folders of worktrees not in use will be deleted, out of $count$total. " +
            "Worktrees whose agent is working or that were built within the last hour keep theirs; the next swift build recreates them."
    }
    override fun swiftBuildsDeleted(count: Int, freed: String, inUse: Int) =
        (if (count == 1) "1 .build folder deleted · $freed freed" else "$count .build folders deleted · $freed freed") +
            if (inUse == 0) "" else " · $inUse kept because in use"
    override val swiftBuildDeleteBlockedDeleting = ".build folders are being deleted"
    override val simulatorStorageTitle = "Simulator storage"
    override val simulatorStorageUnavailable = "The simulator storage couldn't be read"
    override val noSimulatorRuntimes = "No simulator runtimes installed"
    override fun simulatorRuntimesSummary(count: Int, size: String) =
        (if (count == 1) "1 runtime" else "$count runtimes") + " · $size"
    override val runtimeUnused = "Not used"
    override val runtimeBundled = "Comes with Xcode"
    override val runtimeBooted = "A simulator on it is booted"
    override val runtimeDefault = "default for new simulators"
    override fun runtimeUsedBy(users: List<String>) = "Used by " + users.joinToString(", ")
    override val runtimeNeverUsed = "Never used"
    override val sizeUnknown = "Unknown size"
    override val deleteRuntime = "Delete"
    override val confirmDeleteRuntimeTitle = "Delete simulator runtime?"
    override fun confirmDeleteRuntime(name: String, size: String) =
        "$name ($size) will be deleted from this Mac. Xcode can download it again from its Components settings."
    override fun runtimeDeleted(name: String, freed: String, unavailable: Int) =
        "$name deleted, $freed freed" + when (unavailable) {
            0 -> ""
            1 -> ". 1 simulator device is now unavailable"
            else -> ". $unavailable simulator devices are now unavailable"
        }
    override fun unavailableDevices(count: Int, size: String) =
        (if (count == 1) "1 unavailable simulator device" else "$count unavailable simulator devices") + " · $size"
    override val deleteUnavailableDevices = "Delete unavailable devices"
    override val confirmDeleteUnavailableDevicesTitle = "Delete unavailable devices?"
    override fun confirmDeleteUnavailableDevices(count: Int, size: String) =
        (if (count == 1) "1 simulator device whose runtime is gone" else "$count simulator devices whose runtime is gone") +
            " will be deleted with their data ($size)."
    override fun unavailableDevicesDeleted(count: Int, freed: String) =
        (if (count == 1) "1 unavailable device deleted" else "$count unavailable devices deleted") + ", $freed freed"
    override fun dyldCachesSummary(size: String) = "dyld caches · $size"
    override val dyldCacheCurrent = "Current macOS build"
    override val dyldCacheStale = "From another macOS build: remove it yourself as administrator"
    override val copyRemovalCommand = "Copy command"
}
