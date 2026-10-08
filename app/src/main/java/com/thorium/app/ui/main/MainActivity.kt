package com.thorium.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.thorium.app.ThoriumApplication
import com.thorium.app.storage.StoragePermission
import com.thorium.app.ui.companion.CompanionActivity
import com.thorium.core.ui.input.dispatchGamepadKey
import com.thorium.core.ui.theme.ThoriumTheme
import com.thorium.feature.display.DisplayCoordinator
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private companion object {
        const val REQUEST_NOTIFICATIONS = 42
    }

    private val vm: AppViewModel by lazy { (application as ThoriumApplication).appViewModel }

    private val displays by lazy {
        DisplayCoordinator(this, onDisplayAdded = { launchCompanionIfWanted() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.markMainOpen()
        vm.onRequestStoragePermission = { StoragePermission.openSettings(this) }
        vm.onDownloadQueued = ::askForNotifications
        setContent {
            ThoriumTheme {
                // Re-evaluated whenever the user toggles "Dual screen" in the menu.
                LaunchedEffect(vm.companionEnabled) {
                    if (vm.companionEnabled) {
                        launchCompanionIfWanted()
                    } else {
                        // Let the companion close first, then take the input focus back.
                        delay(300)
                        displays.refocus(this@MainActivity)
                    }
                }
                ThoriumApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        displays.start()
    }

    override fun onResume() {
        super.onResume()
        // Also runs when coming back from the system "All files access" screen.
        vm.refreshLibrary()
    }

    override fun onStop() {
        super.onStop()
        displays.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.onRequestStoragePermission = null
        vm.onDownloadQueued = null
        if (isFinishing) vm.markMainClosed()
    }

    /** Android 13+ needs a runtime permission to show the download notification; the download works either way. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun launchCompanionIfWanted() {
        if (vm.companionVisible) displays.launchOnSecondary(this, CompanionActivity::class.java)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchGamepadKey(event, vm::handle) || super.dispatchKeyEvent(event)
}
