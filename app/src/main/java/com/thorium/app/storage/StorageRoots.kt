package com.thorium.app.storage

import android.content.Context
import com.thorium.app.R
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/** A mounted storage volume with the name the system shows to the user. */
data class StorageVolumeInfo(val dir: File, val label: String)

/** Discovers mounted storage volumes (internal storage and SD cards) to scan for games. */
object StorageRoots {

    fun volumes(context: Context): List<StorageVolumeInfo> {
        val manager = context.getSystemService(StorageManager::class.java)
        return manager.storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory
                } else if (volume.isPrimary) {
                    Environment.getExternalStorageDirectory()
                } else {
                    null
                }
                dir?.let {
                    val name = volume.getDescription(context) ?: it.name
                    // A card is often labelled with whatever its owner formatted it as ("3DS").
                    StorageVolumeInfo(it, if (volume.isPrimary) name else context.getString(R.string.sd_card_label, name))
                }
            }
            .distinctBy { it.dir }
    }

    fun detect(context: Context): List<File> = volumes(context).map { it.dir }
}
