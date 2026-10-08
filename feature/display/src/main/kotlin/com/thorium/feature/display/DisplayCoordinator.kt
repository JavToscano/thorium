package com.thorium.feature.display

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

/**
 * Finds the secondary display and launches an activity on it.
 *
 * On the AYN Thor the bottom screen is a regular built-in display flagged as PRESENTATION; an
 * activity can be started there with [ActivityOptions.setLaunchDisplayId]. The coordinator does
 * not know any UI: the caller passes the activity class to launch.
 */
class DisplayCoordinator(
    context: Context,
    private val onDisplayAdded: () -> Unit = {},
    private val onDisplayRemoved: () -> Unit = {},
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = onDisplayAdded()
        override fun onDisplayRemoved(displayId: Int) = onDisplayRemoved()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    /** Starts listening for displays being connected or removed. */
    fun start() = displayManager.registerDisplayListener(listener, null)

    fun stop() = displayManager.unregisterDisplayListener(listener)

    /** First valid display that is not [excludeDisplayId] (the display the main UI lives on). */
    fun secondaryDisplay(excludeDisplayId: Int): Display? =
        displayManager.displays.firstOrNull { it.isValid && it.displayId != excludeDisplayId }

    /**
     * Launches [target] on the secondary display. Returns false when there is none or when the
     * launch fails, so callers can fall back to single-screen mode.
     */
    fun launchOnSecondary(from: Activity, target: Class<out Activity>): Boolean {
        val display = secondaryDisplay(excludeDisplayId = from.display?.displayId ?: Display.DEFAULT_DISPLAY)
            ?: return false
        return try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
            val intent = Intent(from, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            from.startActivity(intent, options.toBundle())
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not launch ${target.simpleName} on display ${display.displayId}", t)
            false
        }
    }

    /**
     * Brings [activity] back to the front on its own display. Closing the activity that held the
     * system input focus on the other display would otherwise leave the focus on that display's
     * launcher, and controller buttons would stop reaching the app.
     */
    fun refocus(activity: Activity) {
        val displayId = activity.display?.displayId ?: return
        try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            val intent = Intent(activity, activity.javaClass)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            activity.startActivity(intent, options.toBundle())
        } catch (t: Throwable) {
            Log.w(TAG, "Could not refocus ${activity.javaClass.simpleName}", t)
        }
    }

    private companion object {
        const val TAG = "DisplayCoordinator"
    }
}
