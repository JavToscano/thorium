package com.thorium.data.db

import com.thorium.core.model.SourceConfig
import com.thorium.core.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the sources the user configured. Passwords are stored encrypted and read back only on demand. */
class SourcesRepository internal constructor(db: ThoriumDatabase, private val secrets: SecretBox) {

    private val dao = db.sourcesDao()

    fun observeSources(): Flow<List<SourceConfig>> = dao.observeAll().map { rows -> rows.map { it.toConfig() } }

    /**
     * Inserts ([SourceConfig.id] == 0) or updates a source. [password]: null keeps the stored one,
     * an empty string removes it, anything else replaces it. Returns the source id.
     */
    suspend fun save(config: SourceConfig, password: String?, now: Long = System.currentTimeMillis()): Long {
        val existing = if (config.id != 0L) dao.get(config.id) else null
        val encrypted = when {
            password == null -> existing?.passwordEnc
            password.isEmpty() -> null
            else -> secrets.encrypt(password)
        }
        if (existing == null) {
            return dao.insert(config.toEntity(encrypted, now, null, null))
        }
        dao.update(config.toEntity(encrypted, existing.createdAt, existing.lastCheckOk, existing.lastCheckedAt))
        return existing.id
    }

    suspend fun config(id: Long): SourceConfig? = dao.get(id)?.toConfig()

    suspend fun remove(id: Long) = dao.delete(id)

    /** The stored password, or an empty string when there is none or it can no longer be decrypted. */
    suspend fun password(id: Long): String =
        dao.get(id)?.passwordEnc?.let(secrets::decrypt).orEmpty()

    suspend fun recordCheck(id: Long, ok: Boolean, now: Long = System.currentTimeMillis()) =
        dao.recordCheck(id, if (ok) 1 else 0, now)
}

private fun SourceEntity.toConfig() = SourceConfig(
    id = id,
    name = name,
    type = runCatching { SourceType.valueOf(type) }.getOrDefault(SourceType.Http),
    location = location,
    username = username,
    hasPassword = passwordEnc != null,
    allowInsecure = allowInsecure,
    enabled = enabled,
    healthy = lastCheckOk?.let { it == 1 },
)

private fun SourceConfig.toEntity(passwordEnc: String?, createdAt: Long, lastCheckOk: Int?, lastCheckedAt: Long?) =
    SourceEntity(
        id = id,
        name = name.trim(),
        type = type.name,
        location = location.trim(),
        username = username.trim(),
        passwordEnc = passwordEnc,
        allowInsecure = allowInsecure,
        enabled = enabled,
        createdAt = createdAt,
        lastCheckOk = lastCheckOk,
        lastCheckedAt = lastCheckedAt,
    )
