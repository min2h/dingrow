package app.mongle.service

import app.mongle.model.PersistedState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned backup file for 백업 내보내기/복원. */
object Backup {
    const val FORMAT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Serializable
    private data class File(val app: String = "dingrow", val version: Int = FORMAT_VERSION, val state: PersistedState)

    fun encode(state: PersistedState): String = json.encodeToString(File.serializer(), File(state = state))

    fun decode(text: String): PersistedState? = runCatching {
        val f = json.decodeFromString(File.serializer(), text)
        if (f.app != "dingrow" || f.version > FORMAT_VERSION) null else f.state
    }.getOrNull()
}
