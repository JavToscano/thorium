package com.thorium.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Where the scanner should look for games. */
data class ScanSettings(
    /** Scan every mounted volume (internal storage and SD cards) automatically. */
    val autoDetectStorage: Boolean = true,
    /** Folders added by hand, in the order they were added. */
    val customRoots: List<String> = emptyList(),
)

class SettingsRepository internal constructor(db: ThoriumDatabase) {

    private val dao = db.settingsDao()

    fun observeScanSettings(): Flow<ScanSettings> =
        combine(dao.observeScanRoots(), dao.observeSetting(KEY_AUTO_DETECT)) { roots, auto ->
            ScanSettings(autoDetectStorage = auto?.let(::parseBoolean) ?: true, customRoots = roots)
        }.distinctUntilChanged()

    /**
     * Current value read straight from the database. A scan must use this rather than the
     * observed state, which may not have caught up yet with a change made a moment ago.
     */
    suspend fun scanSettingsNow(): ScanSettings = ScanSettings(
        autoDetectStorage = dao.getSetting(KEY_AUTO_DETECT)?.let(::parseBoolean) ?: true,
        customRoots = dao.getScanRoots(),
    )

    suspend fun addScanRoot(path: String, now: Long = System.currentTimeMillis()) =
        dao.upsertScanRoot(ScanRootEntity(path, now))

    suspend fun removeScanRoot(path: String) = dao.deleteScanRoot(path)

    suspend fun setAutoDetectStorage(enabled: Boolean) = setBoolean(KEY_AUTO_DETECT, enabled)

    fun observeCompanionEnabled(): Flow<Boolean> =
        dao.observeSetting(KEY_COMPANION).map { it?.let(::parseBoolean) ?: true }.distinctUntilChanged()

    suspend fun isCompanionEnabled(): Boolean = dao.getSetting(KEY_COMPANION)?.let(::parseBoolean) ?: true

    suspend fun setCompanionEnabled(enabled: Boolean) = setBoolean(KEY_COMPANION, enabled)

    private suspend fun setBoolean(key: String, value: Boolean) =
        dao.upsertSetting(SettingEntity(key, if (value) "1" else "0"))

    private fun parseBoolean(raw: String) = raw == "1"

    private companion object {
        const val KEY_AUTO_DETECT = "autoDetectStorage"
        const val KEY_COMPANION = "companionEnabled"
    }
}
