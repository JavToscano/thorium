package com.thorium.app.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/** Discovers mounted storage volumes (internal storage and SD cards) to scan for games. */
object StorageRoots {

    fun detect(context: Context): List<File> {
        val manager = context.getSystemService(StorageManager::class.java)
        val volumes = manager.storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory
                } else if (volume.isPrimary) {
                    Environment.getExternalStorageDirectory()
                } else {
                    null
                }
            }
        return volumes.distinct()
    }
}
