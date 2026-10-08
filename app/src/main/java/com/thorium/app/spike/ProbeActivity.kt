package com.thorium.app.spike

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Launched on the secondary display. Shows which display it really landed on and logs its own
 * key events and lifecycle so we can see where input focus goes.
 */
class ProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpikeLog.add("[Probe] onCreate displayId=${display?.displayId}")
        setContent {
            SpikeTheme {
                ScreenColumn {
                    Text(
                        "PROBE ACTIVITY",
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1B5E20))
                    )
                    Mono("displayId=${display?.displayId}  ${display?.name}", size = 13)
                    Mono(display?.let { DisplayDiagnostics.describeDisplay(it) } ?: "no display")
                    ActionRow("Close" to { finish() }, "Clear log" to { SpikeLog.clear() })
                    LogList(Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onResume() { super.onResume(); SpikeLog.add("[Probe] onResume") }
    override fun onPause() { super.onPause(); SpikeLog.add("[Probe] onPause") }
    override fun onStop() { super.onStop(); SpikeLog.add("[Probe] onStop") }
    override fun onDestroy() { super.onDestroy(); SpikeLog.add("[Probe] onDestroy") }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        SpikeLog.add("[Probe] windowFocus=$hasFocus")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        SpikeLog.key("Probe", event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        SpikeLog.motion("Probe", event)
        return super.dispatchGenericMotionEvent(event)
    }
}
