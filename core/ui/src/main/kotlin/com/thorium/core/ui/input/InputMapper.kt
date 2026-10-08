package com.thorium.core.ui.input

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Turns raw key events into logical actions.
 *
 * The Thor's "Odin Controller" emits duplicates: A also sends DPAD_CENTER, B also sends BACK,
 * SELECT also sends MENU, L3 also sends DPAD_CENTER (all with a non-zero scanCode from a gamepad
 * source). Those are swallowed so one press is one action. The D-pad is read from DPAD keys only;
 * the HAT axes are never consulted.
 */
object InputMapper {

    data class Result(val action: GamepadAction?, val consume: Boolean)

    private val PASS = Result(null, false)
    private val SWALLOW = Result(null, true)

    fun map(event: KeyEvent): Result {
        val key = event.keyCode
        if (isSyntheticDuplicate(event)) return SWALLOW
        val action = when (key) {
            KeyEvent.KEYCODE_DPAD_UP -> GamepadAction.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> GamepadAction.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> GamepadAction.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadAction.Right
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> GamepadAction.Select
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> GamepadAction.Back
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadAction.TabLeft
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadAction.TabRight
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU -> GamepadAction.Menu
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadAction.Secondary
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadAction.Favorite
            else -> null
        } ?: return PASS
        return Result(action, true)
    }

    private fun isSyntheticDuplicate(event: KeyEvent): Boolean {
        val fromGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        if (!fromGamepad || event.scanCode == 0) return false
        return event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_MENU
    }
}
