package com.jakewharton.sdkmanager.internal

import com.android.SdkConstants.*
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException
import java.io.File
import java.util.*

class SdkResolver(
    private val project: Project,
    private val system: System,
    private val downloader: Downloader,
    private val isWindows: Boolean
) {
    private val log: Logger = Logging.getLogger(SdkResolver::class.java)
    private val userHome: File = File(system.property("user.home"))
    private val userAndroid: File = File(userHome, ".android-sdk")
    private val localProperties: File = File(project.rootDir, FN_LOCAL_PROPERTIES)

    fun resolve(): File {
        // Check for existing local.properties file and the SDK it points to.
        if (localProperties.exists()) {
            log.debug("Found $FN_LOCAL_PROPERTIES at '${localProperties.absolutePath}'.")
            val properties = Properties()
            localProperties.inputStream().use { properties.load(it) }
            val sdkDirPath = properties.getProperty(SDK_DIR_PROPERTY)
            if (sdkDirPath != null) {
                log.debug("Found $SDK_DIR_PROPERTY of '$sdkDirPath'.")
                val sdkDir = File(sdkDirPath)
                if (!sdkDir.exists()) {
                    throw StopExecutionException(
                        "Specified SDK directory '$sdkDirPath' in '$FN_LOCAL_PROPERTIES' is not found."
                    )
                }
                return sdkDir
            }

            log.debug("Missing $SDK_DIR_PROPERTY in $FN_LOCAL_PROPERTIES.")
        } else {
            log.debug("Missing $FN_LOCAL_PROPERTIES.")
        }

        // Look for ANDROID_HOME environment variable.
        val androidHome = system.env(ANDROID_HOME_ENV)
        if (androidHome != null && androidHome != "") {
            val sdkDir = File(androidHome)
            if (sdkDir.exists()) {
                log.debug("Found $ANDROID_HOME_ENV at '$androidHome'. Writing to $FN_LOCAL_PROPERTIES.")
                writeLocalProperties(androidHome)
            } else {
                log.debug("Found $ANDROID_HOME_ENV at '$androidHome' but directory is missing.")
                downloadSdk(sdkDir)
            }
            return sdkDir
        }

        log.debug("Missing $ANDROID_HOME_ENV.")

        // Look for an SDK in the home directory.
        if (userAndroid.exists()) {
            log.debug("Found existing SDK at '${userAndroid.absolutePath}'. Writing to $FN_LOCAL_PROPERTIES.")
            writeLocalProperties(userAndroid.absolutePath)
            return userAndroid
        }

        downloadSdk(userAndroid)
        return userAndroid
    }

    private fun downloadSdk(target: File) {
        log.lifecycle("Android SDK not found. Downloading...")

        // Download the SDK zip and extract it.
        downloader.download(target)
        log.lifecycle("SDK extracted at '${target.absolutePath}'. Writing to $FN_LOCAL_PROPERTIES.")

        writeLocalProperties(target.absolutePath)
    }

    private fun writeLocalProperties(path: String) {
        val pathToWrite = if (isWindows) {
            // Escape Windows file separators when writing as a path.
            path.replace("\\", "\\\\")
        } else {
            path
        }

        if (localProperties.exists()) {
            localProperties.appendText("$SDK_DIR_PROPERTY=$pathToWrite\n")
        } else {
            localProperties.writeText(
                "# DO NOT check this file into source control.\n" +
                "$SDK_DIR_PROPERTY=$pathToWrite\n"
            )
        }
    }

    companion object {
        @JvmStatic
        fun resolve(project: Project): File {
            val isWindows = currentPlatform() == PLATFORM_WINDOWS
            return SdkResolver(project, System.Real(), Downloader.Real(), isWindows).resolve()
        }
    }
}
