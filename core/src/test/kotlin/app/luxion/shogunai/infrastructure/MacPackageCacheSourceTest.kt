package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.PackageCacheSizes
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MacPackageCacheSourceTest {

    private val home = "/home/dev"
    private val swiftPm = Path.of(home, "Library/Caches/org.swift.swiftpm").toString()
    private val cocoaPods = Path.of(home, "Library/Caches/CocoaPods").toString()

    @Test
    fun `measures the SwiftPM and CocoaPods caches that exist on macOS`() = runTest {
        val files = FakeFileManager(sizes = mapOf(swiftPm to 300L), directories = setOf(swiftPm))

        assertEquals(PackageCacheSizes(swiftPmBytes = 300L, cocoaPodsBytes = null), MacPackageCacheSource(files, home, "Mac OS X").sizes())
    }

    @Test
    fun `other systems have no package caches to measure`() = runTest {
        val files = FakeFileManager(sizes = mapOf(swiftPm to 300L, cocoaPods to 200L), directories = setOf(swiftPm, cocoaPods))

        assertEquals(PackageCacheSizes(), MacPackageCacheSource(files, home, "Linux").sizes())
    }
}
