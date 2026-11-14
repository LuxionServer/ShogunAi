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
}
