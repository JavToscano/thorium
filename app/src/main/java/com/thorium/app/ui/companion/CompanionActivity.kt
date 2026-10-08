package com.thorium.app.ui.companion

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.thorium.app.ThoriumApplication
import com.thorium.core.ui.input.dispatchGamepadKey
import com.thorium.core.ui.theme.ThoriumTheme

/**
 * Lives on the secondary display. It only reads the shared [com.thorium.app.ui.main.AppViewModel],
 * and also forwards button presses to it, so the controller works whichever display has the
 * system input focus.
 */
class CompanionActivity : ComponentActivity() {

    private val vm by lazy { (application as ThoriumApplication).appViewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThoriumTheme {
                // Close when the user turns "Dual screen" off or the main activity goes away.
                LaunchedEffect(vm.companionVisible) { if (!vm.companionVisible) finish() }
                CompanionScreen(vm)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchGamepadKey(event, vm::handle) || super.dispatchKeyEvent(event)
}
