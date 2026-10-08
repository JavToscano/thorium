package com.thorium.app.spike

import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Phase 0 spike. Answers, on the real device:
 *  1. Which displays exist and with which flags?
 *  2. Can an Activity be launched on the secondary display (launchDisplayId)?
 *  3. Does android.app.Presentation work on it?
 *  4. Which KeyEvents / axes does each physical control produce?
 *
 * Physical shortcuts: A = launch Probe on secondary display, X = try Presentation,
 * Y = clear log, B = close Presentation. Every key is logged anyway.
 */
class SpikeActivity : ComponentActivity() {

    private var displaysText by mutableStateOf("")
    private var inputText by mutableStateOf("")
    private var presentationCount by mutableIntStateOf(0)
    private var presentation: Presentation? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { SpikeLog.add("[Display] added $displayId"); refresh() }
        override fun onDisplayRemoved(displayId: Int) { SpikeLog.add("[Display] removed $displayId"); refresh() }
        override fun onDisplayChanged(displayId: Int) { SpikeLog.add("[Display] changed $displayId"); refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpikeLog.add("[Main] onCreate displayId=${display?.displayId} sdk=${android.os.Build.VERSION.SDK_INT} " +
            "model=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)
        refresh()

        setContent {
            SpikeTheme {
                ScreenColumn {
                    Mono("THORIUM SPIKE  A=probe on 2nd display  X=Presentation  Y=clear  B=close Presentation", size = 12)
                    Mono(displaysText)
                    Mono("PRESENTATION-category displays: $presentationCount", size = 11)
                    Mono("Input devices:\n$inputText")
                    ActionRow(
                        "A: Launch Probe" to ::launchProbe,
                        "X: Presentation" to ::tryPresentation,
                        "B: Close Pres." to ::closePresentation,
                        "Y: Clear" to { SpikeLog.clear() },
                    )
                    LogList(Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onResume() { super.onResume(); SpikeLog.add("[Main] onResume"); refresh() }
    override fun onPause() { super.onPause(); SpikeLog.add("[Main] onPause") }
    override fun onStop() { super.onStop(); SpikeLog.add("[Main] onStop") }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        closePresentation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        SpikeLog.add("[Main] windowFocus=$hasFocus")
    }

    private fun refresh() {
        displaysText = DisplayDiagnostics.allDisplays(this).joinToString("\n") {
            DisplayDiagnostics.describeDisplay(it)
        }
        presentationCount = DisplayDiagnostics.presentationCategory(this).size
        inputText = DisplayDiagnostics.inputDevices(this)
    }

    private fun launchProbe() {
        val target = DisplayDiagnostics.secondary(this)
        if (target == null) {
            SpikeLog.add("[Main] launchProbe: no secondary display found")
            return
        }
        try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
            val intent = Intent(this, ProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent, options.toBundle())
            SpikeLog.add("[Main] launchProbe: requested displayId=${target.displayId}")
        } catch (t: Throwable) {
            SpikeLog.add("[Main] launchProbe FAILED: $t")
        }
    }

    private fun tryPresentation() {
        val target = DisplayDiagnostics.secondary(this)
        if (target == null) {
            SpikeLog.add("[Main] Presentation: no secondary display found")
            return
        }
        closePresentation()
        try {
            val p = object : Presentation(this as Context, target) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(TextView(context).apply {
                        text = "PRESENTATION on display ${target.displayId}"
                        textSize = 22f
                    })
                }
            }
            p.show()
            presentation = p
            SpikeLog.add("[Main] Presentation: shown OK on displayId=${target.displayId}")
        } catch (t: Throwable) {
            SpikeLog.add("[Main] Presentation FAILED: $t")
        }
    }

    private fun closePresentation() {
        presentation?.let {
            runCatching { it.dismiss() }
            SpikeLog.add("[Main] Presentation dismissed")
        }
        presentation = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        SpikeLog.key("Main", event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> { launchProbe(); return true }
                KeyEvent.KEYCODE_BUTTON_X -> { tryPresentation(); return true }
                KeyEvent.KEYCODE_BUTTON_Y -> { SpikeLog.clear(); return true }
                KeyEvent.KEYCODE_BUTTON_B -> { closePresentation(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        SpikeLog.motion("Main", event)
        return super.dispatchGenericMotionEvent(event)
    }
}
