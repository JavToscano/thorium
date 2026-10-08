package com.thorium.core.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.text.UiText

/** A key of the on-screen keyboard. */
sealed interface Key {
    data class Char(val value: String) : Key
    data object Shift : Key
    data object Symbols : Key
    data object Space : Key
    data object Backspace : Key
    data object Done : Key
}

enum class KeyboardPage { Lower, Upper, Symbols }

/**
 * Text entry driven only by the controller. The system keyboard is useless on the Thor: it opens on
 * the wrong screen and cannot be moved with the D-pad.
 *
 * Controls while open: D-pad moves over the keys, **A** types the key under the cursor, **Y**
 * deletes, **SELECT** toggles upper case, **START** accepts, **B** cancels.
 */
class KeyboardController {

    var active by mutableStateOf(false); private set
    var text by mutableStateOf(""); private set
    var title by mutableStateOf<UiText?>(null); private set
    var masked by mutableStateOf(false); private set
    var page by mutableStateOf(KeyboardPage.Lower); private set
    var row by mutableIntStateOf(0); private set
    var col by mutableIntStateOf(0); private set

    private var onDone: ((String) -> Unit)? = null

    val rows: List<List<Key>> get() = Layouts.rows(page)

    fun open(title: UiText, initial: String, masked: Boolean = false, onDone: (String) -> Unit) {
        this.title = title
        this.text = initial
        this.masked = masked
        this.onDone = onDone
        page = KeyboardPage.Lower
        row = 1
        col = 0
        active = true
    }

    /** While the keyboard is open it takes every action, so nothing leaks to the screen below. */
    fun handle(action: GamepadAction): Boolean {
        if (!active) return false
        when (action) {
            GamepadAction.Up -> move(-1, 0)
            GamepadAction.Down -> move(+1, 0)
            GamepadAction.Left -> move(0, -1)
            GamepadAction.Right -> move(0, +1)
            GamepadAction.Select -> press(rows[row][col])
            GamepadAction.Favorite -> backspace()
            GamepadAction.Secondary -> toggleShift()
            GamepadAction.Menu -> finish()
            GamepadAction.Back -> close()
            GamepadAction.TabLeft, GamepadAction.TabRight -> Unit
        }
        return true
    }

    private fun move(dRow: Int, dCol: Int) {
        val newRow = (row + dRow).coerceIn(0, rows.size - 1)
        row = newRow
        col = (col + dCol).coerceIn(0, rows[newRow].size - 1)
    }

    private fun press(key: Key) {
        when (key) {
            is Key.Char -> {
                text += key.value
                // One capital letter at a time, like a phone keyboard.
                if (page == KeyboardPage.Upper) switchTo(KeyboardPage.Lower)
            }
            Key.Space -> text += " "
            Key.Backspace -> backspace()
            Key.Shift -> toggleShift()
            Key.Symbols -> switchTo(if (page == KeyboardPage.Symbols) KeyboardPage.Lower else KeyboardPage.Symbols)
            Key.Done -> finish()
        }
    }

    private fun backspace() {
        if (text.isNotEmpty()) text = text.dropLast(1)
    }

    private fun toggleShift() {
        switchTo(if (page == KeyboardPage.Upper) KeyboardPage.Lower else KeyboardPage.Upper)
    }

    private fun switchTo(newPage: KeyboardPage) {
        page = newPage
        // Pages have the same shape, but clamp anyway so the cursor can never point outside a row.
        row = row.coerceIn(0, rows.size - 1)
        col = col.coerceIn(0, rows[row].size - 1)
    }

    private fun finish() {
        val callback = onDone
        close()
        callback?.invoke(text)
    }

    private fun close() {
        active = false
        onDone = null
    }
}

internal object Layouts {

    private fun chars(s: String): List<Key> = s.map { Key.Char(it.toString()) }
    private val bottom = listOf(Key.Shift, Key.Symbols, Key.Space, Key.Backspace, Key.Done)

    private val lower = listOf(
        chars("1234567890"),
        chars("qwertyuiop"),
        chars("asdfghjkl-"),
        chars("zxcvbnm./:"),
        bottom,
    )
    private val upper = listOf(
        chars("1234567890"),
        chars("QWERTYUIOP"),
        chars("ASDFGHJKL_"),
        chars("ZXCVBNM,?!"),
        bottom,
    )
    private val symbols = listOf(
        chars("!@#$%^&*()"),
        chars("-_=+[]{}\\|"),
        chars(";:'\"<>/?,."),
        chars("~`@#%&*()+"),
        bottom,
    )

    fun rows(page: KeyboardPage): List<List<Key>> = when (page) {
        KeyboardPage.Lower -> lower
        KeyboardPage.Upper -> upper
        KeyboardPage.Symbols -> symbols
    }
}
