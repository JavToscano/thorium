package com.thorium.feature.launcher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Display
import androidx.core.content.FileProvider
import java.io.File

/** What happened when a game was started. */
sealed interface LaunchResult {
    data class Started(val emulator: EmulatorProfile) : LaunchResult
    /** No installed emulator runs this platform. */
    data object NoEmulator : LaunchResult
    data object FileMissing : LaunchResult
    data class Failed(val emulator: EmulatorProfile, val reason: String) : LaunchResult
}

/** Finds the installed emulators and starts games in them. */
class EmulatorLauncher(
    private val context: Context,
    private val profiles: List<EmulatorProfile>,
) {

    fun installedFor(platformId: String): List<EmulatorProfile> =
        EmulatorProfiles.forPlatform(profiles, platformId).filter { isInstalled(it.packageName) }

    /** Starts the game in the first installed emulator that runs [platformId]. */
    fun launch(paths: List<String>, platformId: String): LaunchResult {
        val profile = installedFor(platformId).firstOrNull() ?: return LaunchResult.NoEmulator
        val file = PrimaryFile.pick(paths)?.let(::File)?.takeIf { it.isFile } ?: return LaunchResult.FileMissing
        return try {
            val intent = Intent(profile.action).setClassName(profile.packageName, profile.activity)
            when (profile.delivery) {
                Delivery.Uri -> {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    context.grantUriPermission(profile.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.setDataAndType(uri, profile.mimeType)
                    ExtrasTemplate.expand(profile.extras, file.path, uri.toString()).forEach { (k, v) -> intent.putExtra(k, v) }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Delivery.Path -> {
                    ExtrasTemplate.expand(profile.extras, file.path, null).forEach { (k, v) -> intent.putExtra(k, v) }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // The emulator takes over the main screen, where the player is looking.
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context.startActivity(intent, options.toBundle())
            LaunchResult.Started(profile)
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Failed(profile, e.message.orEmpty())
        } catch (e: SecurityException) {
            LaunchResult.Failed(profile, e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            LaunchResult.Failed(profile, e.message.orEmpty())
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        /** Reads the emulator profiles bundled in the app's assets. */
        fun load(context: Context): EmulatorLauncher {
            val text = context.assets.open("emulators.json").bufferedReader().use { it.readText() }
            return EmulatorLauncher(context, EmulatorProfiles.parse(text))
        }
    }
}
