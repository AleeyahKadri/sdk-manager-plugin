package com.jakewharton.sdkmanager

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.jakewharton.sdkmanager.internal.PackageResolver
import com.jakewharton.sdkmanager.internal.SdkResolver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.StopExecutionException
import java.util.concurrent.TimeUnit

class SdkManagerPlugin : Plugin<Project> {
    private val log: Logger = Logging.getLogger(SdkManagerPlugin::class.java)

    override fun apply(project: Project) {
        if (hasAndroidPlugin(project)) {
            throw StopExecutionException(
                "Must be applied before 'android' or 'android-library' plugin."
            )
        }

        if (isOfflineBuild(project)) {
            log.debug("Offline build. Skipping package resolution.")
            return
        }

        project.extensions.create("sdkManager", SdkManagerExtension::class.java)

        // Eager resolve the SDK and local.properties pointer.
        val sdk = time("SDK resolve") {
            SdkResolver.resolve(project)
        }

        // Defer resolving SDK package dependencies until after the model is finalized.
        project.afterEvaluate {
            if (project.state.failure != null) {
                return@afterEvaluate
            }

            if (!hasAndroidPlugin(project)) {
                log.debug("No Android plugin detecting. Skipping package resolution.")
                return@afterEvaluate
            }

            time("Package resolve") {
                PackageResolver.resolve(project, sdk)
            }
        }
    }

    private fun <T> time(name: String, task: () -> T): T {
        val before = System.nanoTime()
        val result = task()
        val after = System.nanoTime()
        val took = TimeUnit.NANOSECONDS.toMillis(after - before)
        log.info("$name took $took ms.")
        return result
    }

    companion object {
        @JvmStatic
        fun hasAndroidPlugin(project: Project): Boolean {
            return project.plugins.hasPlugin(AppPlugin::class.java) ||
                   project.plugins.hasPlugin(LibraryPlugin::class.java)
        }

        @JvmStatic
        fun isOfflineBuild(project: Project): Boolean {
            return project.gradle.startParameter.isOffline
        }
    }
}
