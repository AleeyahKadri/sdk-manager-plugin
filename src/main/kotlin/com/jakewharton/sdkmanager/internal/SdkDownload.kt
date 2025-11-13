package com.jakewharton.sdkmanager.internal

import com.android.SdkConstants.*
import org.apache.commons.io.FileUtils
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.ArchiveFormat.*
import org.rauschig.jarchivelib.CompressionType.*
import java.io.File
import java.net.URL

/** Manages platform-specific SDK downloads. */
enum class SdkDownload(
    private val suffix: String,
    private val ext: String
) {
    WINDOWS("windows", "zip"),
    LINUX("linux", "tgz"),
    DARWIN("macosx", "zip");

    private val log: Logger = Logging.getLogger(SdkDownload::class.java)

    /** Download the SDK to [dest] and extract. */
    fun download(dest: File) {
        val url = "http://dl.google.com/android/android-sdk_r$SDK_VERSION_MAJOR-$suffix.$ext"
        log.debug("Downloading SDK from $url.")

        val temp = File(dest.parentFile, "android-sdk.temp")
        temp.outputStream().use { output ->
            output.write(URL(url).readBytes())
        }

        // Archives have a single child folder. Extract to the parent directory.
        val parentFile = temp.parentFile
        log.debug("Extracting SDK to ${parentFile.absolutePath}.")
        getArchiver().extract(temp, parentFile)

        // Move the aforementioned child folder to the real destination.
        val extracted = File(parentFile, "android-sdk-$suffix")
        FileUtils.moveDirectory(extracted, dest)

        // Delete downloaded archive.
        temp.delete()
    }

    private fun getArchiver() = when (ext) {
        "zip" -> ArchiverFactory.createArchiver(ZIP)
        "tgz" -> ArchiverFactory.createArchiver(TAR, GZIP)
        else -> throw IllegalArgumentException("Unknown archive format '$ext'.")
    }

    companion object {
        const val SDK_VERSION_MAJOR = "24.4.1"

        @JvmStatic
        fun get(): SdkDownload = when (currentPlatform()) {
            PLATFORM_WINDOWS -> WINDOWS
            PLATFORM_LINUX -> LINUX
            PLATFORM_DARWIN -> DARWIN
            else -> throw IllegalStateException("Unknown platform.")
        }
    }
}
