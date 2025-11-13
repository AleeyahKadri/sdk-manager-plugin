package com.jakewharton.sdkmanager.internal

import com.android.SdkConstants.*
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException
import java.io.File
import java.io.FileInputStream
import java.util.*
import java.util.regex.Pattern

class PackageResolver(
    private val project: Project,
    private val sdk: File,
    private val androidCommand: AndroidCommand
) {
    private val log: Logger = Logging.getLogger(PackageResolver::class.java)
    private val buildToolsDir: File = File(sdk, FD_BUILD_TOOLS)
    private val platformToolsDir: File = File(sdk, FD_PLATFORM_TOOLS)
    private val platformsDir: File = File(sdk, FD_PLATFORMS)
    private val addonsDir: File = File(sdk, FD_ADDONS)
    private val androidRepositoryDir: File
    private val googleRepositoryDir: File

    init {
        val extrasDir = File(sdk, FD_EXTRAS)
        val androidExtrasDir = File(extrasDir, "android")
        androidRepositoryDir = File(androidExtrasDir, FD_M2_REPOSITORY)
        val googleExtrasDir = File(extrasDir, "google")
        googleRepositoryDir = File(googleExtrasDir, FD_M2_REPOSITORY)
    }

    fun resolve() {
        resolveBuildTools()
        resolvePlatformTools()
        resolveCompileVersion()
        resolveSupportLibraryRepository()
        resolvePlayServiceRepository()
        resolveEmulator()
    }

    private fun resolveBuildTools() {
        val android = project.extensions.getByName("android")
        val buildToolsRevision = android.javaClass.getMethod("getBuildToolsRevision").invoke(android)
        log.debug("Build tools version: $buildToolsRevision")

        val buildToolsRevisionDir = File(buildToolsDir, buildToolsRevision.toString())
        if (folderExists(buildToolsRevisionDir)) {
            log.debug("Build tools found!")
            return
        }

        log.lifecycle("Build tools $buildToolsRevision missing. Downloading...")

        val code = androidCommand.update("build-tools-$buildToolsRevision")
        if (code != 0) {
            throw StopExecutionException("Build tools download failed with code $code.")
        }
    }

    private fun resolvePlatformTools() {
        if (folderExists(platformToolsDir)) {
            log.debug("Platform tools found!")
            return
        }

        log.lifecycle("Platform tools missing. Downloading...")

        val code = androidCommand.update("platform-tools")
        if (code != 0) {
            throw StopExecutionException("Platform tools download failed with code $code.")
        }
    }

    private fun resolveCompileVersion() {
        val android = project.extensions.getByName("android")
        val compileVersion: String = android.javaClass.getMethod("getCompileSdkVersion").invoke(android) as String
        log.debug("Compile API version: $compileVersion")

        when {
            compileVersion.startsWith(GOOGLE_API_PREFIX) -> {
                // The google SDK requires the base android SDK as a prerequisite, but
                // the SDK manager won't follow dependencies automatically.
                val baseVersion = compileVersion.replace(GOOGLE_API_PREFIX, "android-")
                installIfMissing(platformsDir, baseVersion)
                val addonVersion = compileVersion.replace(GOOGLE_API_PREFIX, "addon-google_apis-google-")
                installIfMissing(addonsDir, addonVersion)
            }
            compileVersion.startsWith(GOOGLE_GDK_PREFIX) -> {
                val gdkVersion = compileVersion.replace(GOOGLE_GDK_PREFIX, "addon-google_gdk-google-")
                installIfMissing(platformsDir, gdkVersion)
            }
            else -> {
                installIfMissing(platformsDir, compileVersion)
            }
        }
    }

    private fun installIfMissing(baseDir: File, version: String) {
        val existingDir = File(baseDir, version)
        if (folderExists(existingDir)) {
            log.debug("Compilation API $version found!")
            return
        }

        log.lifecycle("Compilation API $version missing. Downloading...")

        val code = androidCommand.update(version)
        if (code != 0) {
            throw StopExecutionException("Compilation API $version download failed with code $code.")
        }
    }

    private fun resolveSupportLibraryRepository() {
        val supportDeps = findDependenciesStartingWith("com.android.support")

        if (supportDeps.isEmpty()) {
            log.debug("No support library dependency found.")
            return
        }

        log.debug("Found support library dependencies: $supportDeps")

        project.repositories.maven { maven ->
            maven.setUrl(androidRepositoryDir)
        }

        var needsDownload = false
        if (!folderExists(androidRepositoryDir)) {
            needsDownload = true
            log.lifecycle("Support library repository missing. Downloading...")
        } else if (!dependenciesAvailable(supportDeps)) {
            needsDownload = true
            log.lifecycle("Support library repository outdated. Downloading update...")
        }

        if (needsDownload) {
            val code = androidCommand.update("extra-android-m2repository")
            if (code != 0) {
                throw StopExecutionException("Support repository download failed with code $code.")
            }
        }
    }

    private fun resolvePlayServiceRepository() {
        val playServicesDeps = findDependenciesWithGroup("com.google.android.gms")
        if (playServicesDeps.isEmpty()) {
            log.debug("No Google Play Services dependency found.")
            return
        }

        log.debug("Found Google Play Services dependencies: $playServicesDeps")

        project.repositories.apply {
            maven { maven -> maven.setUrl(androidRepositoryDir) }
            maven { maven -> maven.setUrl(googleRepositoryDir) }
        }

        var needsDownload = false
        if (!folderExists(googleRepositoryDir)) {
            needsDownload = true
            log.lifecycle("Google Play Services repository missing. Downloading...")
        } else if (!dependenciesAvailable(playServicesDeps)) {
            needsDownload = true
            log.lifecycle("Google Play Services repository outdated. Downloading update...")
        }

        if (needsDownload) {
            val code = androidCommand.update("extra-google-m2repository")
            if (code != 0) {
                throw StopExecutionException(
                    "Google Play Services repository download failed with code $code."
                )
            }
        }
    }

    private fun resolveEmulator() {
        val sdkManager = project.extensions.getByName("sdkManager") as com.jakewharton.sdkmanager.SdkManagerExtension
        val emulatorVersion = sdkManager.emulatorVersion
        if (emulatorVersion == null) {
            log.debug("No emulator defined")
            return
        }

        var emulatorArchitecture = sdkManager.emulatorArchitecture
        if (emulatorArchitecture == null) {
            emulatorArchitecture = "armeabi-v7a"
            log.debug("No architecture specified, defaulting to armeabi-v7a")
        }

        log.debug("Found emulator: $emulatorVersion $emulatorArchitecture")

        val emulatorDir = File(sdk, "$FD_SYSTEM_IMAGES/$emulatorVersion/$emulatorArchitecture")
        val alternativeEmulatorDir = File(sdk, "$FD_SYSTEM_IMAGES/$emulatorVersion/default/$emulatorArchitecture")
        val emulatorPackage = "sys-img-$emulatorArchitecture-$emulatorVersion"
        var needsDownload = false

        if (!folderExists(emulatorDir) && !folderExists(alternativeEmulatorDir)) {
            needsDownload = true
            log.lifecycle("Emulator $emulatorVersion $emulatorArchitecture missing. Downloading...")
        } else {
            var emulatorPropertiesFile = File(emulatorDir, "source.properties")
            if (!emulatorPropertiesFile.canRead()) {
                emulatorPropertiesFile = File(alternativeEmulatorDir, "source.properties")
                if (!emulatorPropertiesFile.canRead()) {
                    throw StopExecutionException("Could not read ${emulatorPropertiesFile.absolutePath}")
                }
            }

            val emulatorProperties = Properties()
            emulatorProperties.load(FileInputStream(emulatorPropertiesFile))
            val emulatorRevision = emulatorProperties.getProperty("Pkg.Revision")
                ?: throw StopExecutionException("Could not get the installed emulator revision for $emulatorPackage")

            val currentEmulatorInfo = androidCommand.list(emulatorPackage)
            if (currentEmulatorInfo.isEmpty()) {
                throw StopExecutionException("Could not get the current emulator revision for $emulatorPackage")
            }

            val matcher = Pattern.compile("Revision\\ ([0-9]+)").matcher(currentEmulatorInfo)
            if (!matcher.find()) {
                throw StopExecutionException("Could not find the current emulator revision for $emulatorPackage")
            }

            if (emulatorRevision.toInt() < matcher.group(1).toInt()) {
                needsDownload = true
                log.lifecycle("Emulator $emulatorVersion $emulatorArchitecture outdated. Downloading update...")
            }
        }

        if (needsDownload) {
            val code = androidCommand.update(emulatorPackage)
            if (code != 0) {
                throw StopExecutionException(
                    "Emulator $emulatorVersion $emulatorArchitecture download failed with code $code."
                )
            }
        }
    }

    private fun findDependenciesWithGroup(group: String): List<Dependency> {
        val deps = mutableListOf<Dependency>()
        for (configuration: Configuration in project.configurations) {
            for (dependency: Dependency in configuration.dependencies) {
                if (group == dependency.group) {
                    deps.add(dependency)
                }
            }
        }
        return deps
    }

    private fun findDependenciesStartingWith(prefix: String): List<Dependency> {
        val deps = mutableListOf<Dependency>()
        for (configuration: Configuration in project.configurations) {
            for (dependency: Dependency in configuration.dependencies) {
                if (dependency.group != null && dependency.group!!.startsWith(prefix)) {
                    deps.add(dependency)
                }
            }
        }
        return deps
    }

    private fun dependenciesAvailable(deps: List<Dependency>): Boolean {
        return try {
            project.configurations.detachedConfiguration(*deps.toTypedArray()).files
            true
        } catch (ignored: Exception) {
            false
        }
    }

    companion object {
        private const val GOOGLE_API_PREFIX = "Google Inc.:Google APIs:"
        private const val GOOGLE_GDK_PREFIX = "Google Inc.:Glass Development Kit Preview:"

        @JvmStatic
        fun resolve(project: Project, sdk: File) {
            PackageResolver(project, sdk, AndroidCommand.Real(sdk, System.Real())).resolve()
        }

        private fun folderExists(folder: File): Boolean {
            return folder.exists() && folder.list()?.isNotEmpty() == true
        }
    }
}

