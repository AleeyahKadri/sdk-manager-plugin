package com.jakewharton.sdkmanager.internal

import com.android.SdkConstants.*
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File

interface AndroidCommand {
    fun update(filter: String): Int
    fun list(filter: String): String

    class Real(sdk: File, private val system: System) : AndroidCommand {
        private val log: Logger = Logging.getLogger(Real::class.java)
        private val androidExecutable: File

        init {
            val toolsDir = File(sdk, FD_TOOLS)
            androidExecutable = File(toolsDir, androidCmdName())
        }

        override fun update(filter: String): Int {
            // -a == all
            // -t == filter
            val options = listOf("-a", "-t", filter)
            val cmd = generateCommand("update", options)
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            // Press 'y' and then enter on the license prompt.
            process.outputStream.bufferedWriter().use { output ->
                output.write("y\n")
            }

            // Pipe the command output to our log.
            process.inputStream.bufferedReader().use { input ->
                var line: String?
                while (input.readLine().also { line = it } != null) {
                    log.debug(line)
                }
            }

            return process.waitFor()
        }

        override fun list(filter: String): String {
            // -a == all
            // -e == extended
            val cmd = generateCommand("list", listOf("-a", "-e"))
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            // Pipe the command output to our log.
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { input ->
                var line: String?
                while (input.readLine().also { line = it } != null) {
                    log.debug(line)
                    output.append(line)
                }
            }

            process.waitFor()

            return output.toString()
                .split("----------")
                .filter { it.contains(filter) }
                .joinToString("")
        }

        private fun generateCommand(command: String, options: List<String>?): List<String> {
            // -u == no UI
            val result = mutableListOf(androidExecutable.absolutePath, command, "sdk", "-u")
            if (options != null) {
                result.addAll(options)
            }

            // --proxy-host == hostname of a proxy server
            // --proxy-port == port of a proxy server
            val proxyHost = system.property("http.proxyHost")
            val proxyPort = system.property("http.proxyPort")
            if (proxyHost != null && proxyPort != null) {
                result.addAll(listOf("--proxy-host", proxyHost, "--proxy-port", proxyPort))
            }

            return result
        }
    }
}
