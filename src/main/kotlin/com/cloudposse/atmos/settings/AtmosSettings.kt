package com.cloudposse.atmos.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File

/**
 * Persistent application-level settings for the Atmos plugin.
 */
@State(
    name = "com.cloudposse.atmos.settings.AtmosSettings",
    storages = [Storage("AtmosSettings.xml")]
)
class AtmosSettings : PersistentStateComponent<AtmosSettings> {

    companion object {
        @JvmStatic
        fun getInstance(): AtmosSettings {
            return ApplicationManager.getApplication().getService(AtmosSettings::class.java)
        }

        // Common locations where atmos might be installed
        private val COMMON_ATMOS_PATHS = listOf(
            "/usr/local/bin/atmos",
            "/usr/bin/atmos",
            "/opt/homebrew/bin/atmos",
            System.getProperty("user.home") + "/.local/bin/atmos",
            System.getProperty("user.home") + "/bin/atmos"
        )
    }

    /**
     * Path to the Atmos executable. If empty, auto-detection will be used.
     */
    var atmosExecutablePath: String = ""

    /**
     * Whether to validate stack files on save.
     */
    var validateOnSave: Boolean = false

    /**
     * Whether to show resolved values inline in the editor.
     */
    var showResolvedValuesInline: Boolean = true

    override fun getState(): AtmosSettings = this

    override fun loadState(state: AtmosSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Gets the effective Atmos executable path.
     * If a custom path is set, returns that. Otherwise, tries to auto-detect.
     */
    fun getEffectiveAtmosPath(): String? {
        if (atmosExecutablePath.isNotBlank()) {
            return atmosExecutablePath.takeIf { File(it).exists() }
        }
        return autoDetectAtmosPath()
    }

    /**
     * Tries to auto-detect the Atmos executable path.
     */
    fun autoDetectAtmosPath(): String? {
        // First, try common installation paths
        for (path in COMMON_ATMOS_PATHS) {
            if (File(path).exists() && File(path).canExecute()) {
                return path
            }
        }

        // Try to find in PATH using 'which' command
        return try {
            val process = ProcessBuilder("which", "atmos")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank() && File(result).exists()) {
                result
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if Atmos is available (either configured or auto-detected).
     */
    fun isAtmosAvailable(): Boolean {
        return getEffectiveAtmosPath() != null
    }
}
