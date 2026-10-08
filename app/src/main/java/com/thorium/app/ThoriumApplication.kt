package com.thorium.app

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.thorium.app.downloads.DownloadManager
import com.thorium.app.downloads.DownloadService
import com.thorium.app.storage.StoragePermission
import com.thorium.app.storage.StorageRoots
import com.thorium.app.ui.main.AppViewModel
import com.thorium.core.model.CoverArt
import com.thorium.core.model.GameCatalog
import com.thorium.data.db.ThoriumData
import com.thorium.data.library.TitleNormalizer
import com.thorium.data.metadata.BundledCatalog
import com.thorium.data.metadata.LibretroCovers
import com.thorium.feature.launcher.EmulatorLauncher

/**
 * Owns the single [AppViewModel] shared by every activity (one per display), so the top and
 * bottom screens always show the same selection.
 */
class ThoriumApplication : Application(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    /** The download queue, created together with the view model. */
    var downloads: DownloadManager? = null
        private set

    /** The bundled metadata catalog; opened on first use. */
    val catalog: GameCatalog by lazy { BundledCatalog.open(this) { TitleNormalizer.parse(it).matchKey } }

    /** Starts games in the installed emulators. */
    val launcher: EmulatorLauncher by lazy { EmulatorLauncher.load(this) }

    /** Box art, downloaded on demand into the app's cache. */
    val covers: CoverArt by lazy { LibretroCovers.create(java.io.File(cacheDir, "covers")) }

    val appViewModel: AppViewModel by lazy {
        ViewModelProvider(this)[AppViewModel::class.java].also {
            it.permissionGranted = StoragePermission::isGranted
            it.catalogProvider = { catalog }
            it.coverProvider = { covers }
            it.launcherProvider = { launcher }
            it.storageRoots = { StorageRoots.detect(this) }
            it.storageVolumes = { StorageRoots.volumes(this) }
            val data = ThoriumData.create(this)
            it.attachData(data)
            downloads = DownloadManager(data) { it.currentScanRoots() }.also { manager ->
                it.downloads = manager
                manager.start()
                watchDownloads(manager)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it.languageController = AppLanguage(this)
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Starts the foreground service whenever there is download work and stops caring when there is none. */
    private fun watchDownloads(manager: DownloadManager) {
        appScope.launch {
            manager.items
                .map { items -> items.any { it.state.isWorkingForService } }
                .distinctUntilChanged()
                .collect { working ->
                    if (working) {
                        try {
                            startForegroundService(Intent(this@ThoriumApplication, DownloadService::class.java))
                        } catch (e: Exception) {
                            // Starting from the background can be refused; the engine keeps going while the process lives.
                        }
                    }
                }
        }
    }

    private val com.thorium.core.model.DownloadState.isWorkingForService: Boolean
        get() = this == com.thorium.core.model.DownloadState.Queued || isActive
}
