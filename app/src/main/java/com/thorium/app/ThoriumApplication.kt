package com.thorium.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.thorium.app.storage.StoragePermission
import com.thorium.app.storage.StorageRoots
import com.thorium.app.ui.main.AppViewModel
import com.thorium.data.db.LibraryRepository

/**
 * Owns the single [AppViewModel] shared by every activity (one per display), so the top and
 * bottom screens always show the same selection.
 */
class ThoriumApplication : Application(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    val appViewModel: AppViewModel by lazy {
        ViewModelProvider(this)[AppViewModel::class.java].also {
            it.permissionGranted = StoragePermission::isGranted
            it.storageRoots = { StorageRoots.detect(this) }
            it.attachRepository(LibraryRepository.create(this))
        }
    }
}
