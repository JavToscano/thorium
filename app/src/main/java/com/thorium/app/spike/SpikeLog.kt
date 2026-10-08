package com.thorium.app.spike

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory + logcat log shared by every spike Activity (single process). */
object SpikeLog {
    const val TAG = "ThoriumSpike"
    private const val MAX = 200
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    val lines = mutableStateListOf<String>()

    fun add(message: String) {
        Log.i(TAG, message)
        lines.add(0, "${clock.format(Date())} $message")
        while (lines.size > MAX) lines.removeAt(lines.size - 1)
    }

    fun clear() = lines.clear()

    fun key(source: String, event: KeyEvent) {
        val action = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP  "
        val dev = event.device?.name ?: "device=${event.deviceId}"
        add(
            "[$source] KEY $action ${KeyEvent.keyCodeToString(event.keyCode)} " +
                "(${event.keyCode}) scan=${event.scanCode} rep=${event.repeatCount} " +
                "src=0x${event.source.toString(16)} dev=\"$dev\""
        )
    }

    private val trackedAxes = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS
    )
    private var lastMotionAt = 0L

    /** Throttled: sticks emit a flood of events. Only axes beyond a small deadzone are shown. */
    fun motion(source: String, event: MotionEvent) {
        val now = System.currentTimeMillis()
        if (now - lastMotionAt < 150) return
        val active = trackedAxes.toList().mapNotNull { axis ->
            val v = event.getAxisValue(axis)
            if (kotlin.math.abs(v) > 0.2f) "${MotionEvent.axisToString(axis)}=${"%.2f".format(v)}" else null
        }
        if (active.isEmpty()) return
        lastMotionAt = now
        add("[$source] MOTION ${active.joinToString(" ")} src=0x${event.source.toString(16)}")
    }
}
