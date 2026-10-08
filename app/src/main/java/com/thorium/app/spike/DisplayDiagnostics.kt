package com.thorium.app.spike

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice

object DisplayDiagnostics {

    private fun flagNames(flags: Int): String {
        val names = buildList {
            if (flags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0) add("PROTECTED_BUFFERS")
            if (flags and Display.FLAG_SECURE != 0) add("SECURE")
            if (flags and Display.FLAG_PRIVATE != 0) add("PRIVATE")
            if (flags and Display.FLAG_PRESENTATION != 0) add("PRESENTATION")
            if (flags and Display.FLAG_ROUND != 0) add("ROUND")
        }
        return "0x${flags.toString(16)}" + if (names.isEmpty()) "" else " ${names.joinToString("|")}"
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($state)"
    }

    @Suppress("DEPRECATION")
    fun describeDisplay(d: Display): String {
        val m = DisplayMetrics()
        d.getRealMetrics(m)
        return buildString {
            append("Display ${d.displayId} \"${d.name}\"\n")
            append("  size=${m.widthPixels}x${m.heightPixels} dpi=${m.densityDpi} ")
            append("refresh=${"%.0f".format(d.refreshRate)}Hz\n")
            append("  state=${stateName(d.state)} valid=${d.isValid} ")
            append("default=${d.displayId == Display.DEFAULT_DISPLAY}\n")
            append("  flags=${flagNames(d.flags)}")
        }
    }

    fun allDisplays(context: Context): List<Display> {
        val dm = context.getSystemService(DisplayManager::class.java)
        return dm.getDisplays().toList()
    }

    fun presentationCategory(context: Context): List<Display> {
        val dm = context.getSystemService(DisplayManager::class.java)
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()
    }

    /** First display that is not the default one and is currently valid. */
    fun secondary(context: Context): Display? =
        allDisplays(context).firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }

    fun inputDevices(context: Context): String {
        val im = context.getSystemService(InputManager::class.java)
        return im.inputDeviceIds.toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual }
            .joinToString("\n") { d ->
                "#${d.id} \"${d.name}\" src=0x${d.sources.toString(16)} " +
                    "vid=${d.vendorId.toString(16)} pid=${d.productId.toString(16)}"
            }
    }
}
