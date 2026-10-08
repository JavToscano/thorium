package com.thorium.core.ui.input

import android.view.KeyEvent

/**
 * Shared key handling for every activity (one per display). Returns true when the event was
 * consumed. Directions repeat while held; every other action fires once per press.
 */
fun dispatchGamepadKey(event: KeyEvent, onAction: (GamepadAction) -> Unit): Boolean {
    val result = InputMapper.map(event)
    if (!result.consume) return false
    val action = result.action
    if (action != null && event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action.repeatable)) {
        onAction(action)
    }
    return true
}
