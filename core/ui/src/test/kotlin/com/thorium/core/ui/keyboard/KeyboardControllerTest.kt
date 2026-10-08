package com.thorium.core.ui.keyboard

import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardControllerTest {

    private val title = UiText.Raw("test")
    private var result: String? = null

    private fun open(initial: String = "", masked: Boolean = false) =
        KeyboardController().also { it.open(title, initial, masked) { text -> result = text } }

    private fun KeyboardController.press(vararg actions: GamepadAction) = actions.forEach { handle(it) }

    /** Moves the cursor to the first key matching [target] by scanning the current page. */
    private fun KeyboardController.goTo(target: Key) {
        for (r in rows.indices) for (c in rows[r].indices) {
            if (rows[r][c] == target) {
                while (row < r) handle(GamepadAction.Down)
                while (row > r) handle(GamepadAction.Up)
                while (col < c) handle(GamepadAction.Right)
                while (col > c) handle(GamepadAction.Left)
                return
            }
        }
        error("key $target not on page")
    }

    private fun KeyboardController.type(text: String) = text.forEach { ch ->
        goTo(Key.Char(ch.toString()))
        handle(GamepadAction.Select)
    }

    @Test
    fun `starts closed and ignores actions`() {
        val kb = KeyboardController()
        assertFalse(kb.active)
        assertFalse(kb.handle(GamepadAction.Select))
    }

    @Test
    fun `open sets the initial text and takes every action`() {
        val kb = open(initial = "abc")
        assertTrue(kb.active)
        assertEquals("abc", kb.text)
        assertTrue(kb.handle(GamepadAction.TabLeft))
    }

    @Test
    fun `typing keys appends to the text`() {
        val kb = open()
        kb.type("a1")
        assertEquals("a1", kb.text)
    }

    @Test
    fun `typing a url with the controller keys`() {
        val kb = open()
        kb.type("http://nas.lan/roms")
        assertEquals("http://nas.lan/roms", kb.text)
    }

    @Test
    fun `Y deletes the last character`() {
        val kb = open(initial = "abc")
        kb.press(GamepadAction.Favorite)
        assertEquals("ab", kb.text)
        kb.press(GamepadAction.Favorite, GamepadAction.Favorite, GamepadAction.Favorite)
        assertEquals("", kb.text)
    }

    @Test
    fun `shift gives one capital letter and goes back to lower case`() {
        val kb = open()
        kb.press(GamepadAction.Secondary)
        assertEquals(KeyboardPage.Upper, kb.page)
        kb.goTo(Key.Char("Q"))
        kb.press(GamepadAction.Select)
        assertEquals("Q", kb.text)
        assertEquals(KeyboardPage.Lower, kb.page)
    }

    @Test
    fun `the symbols key switches pages and back`() {
        val kb = open()
        kb.goTo(Key.Symbols)
        kb.press(GamepadAction.Select)
        assertEquals(KeyboardPage.Symbols, kb.page)
        kb.goTo(Key.Char("@"))
        kb.press(GamepadAction.Select)
        assertEquals("@", kb.text)
        kb.goTo(Key.Symbols)
        kb.press(GamepadAction.Select)
        assertEquals(KeyboardPage.Lower, kb.page)
    }

    @Test
    fun `space and the on-screen backspace key work`() {
        val kb = open(initial = "a")
        kb.goTo(Key.Space); kb.press(GamepadAction.Select)
        assertEquals("a ", kb.text)
        kb.goTo(Key.Backspace); kb.press(GamepadAction.Select)
        assertEquals("a", kb.text)
    }

    @Test
    fun `START accepts and returns the text`() {
        val kb = open(initial = "hello")
        kb.press(GamepadAction.Menu)
        assertFalse(kb.active)
        assertEquals("hello", result)
    }

    @Test
    fun `the Done key accepts too`() {
        val kb = open(initial = "x")
        kb.goTo(Key.Done); kb.press(GamepadAction.Select)
        assertEquals("x", result)
    }

    @Test
    fun `B cancels without calling back`() {
        val kb = open(initial = "keep")
        kb.type("z")
        kb.press(GamepadAction.Back)
        assertFalse(kb.active)
        assertNull(result)
    }

    @Test
    fun `the cursor stays inside the keys`() {
        val kb = open()
        repeat(20) { kb.press(GamepadAction.Left, GamepadAction.Up) }
        assertEquals(0, kb.row); assertEquals(0, kb.col)
        repeat(20) { kb.press(GamepadAction.Right, GamepadAction.Down) }
        assertEquals(kb.rows.size - 1, kb.row)
        assertEquals(kb.rows.last().size - 1, kb.col)
    }

    @Test
    fun `moving to a shorter row keeps the cursor in range`() {
        val kb = open()
        kb.goTo(Key.Char("0"))          // last column of the number row
        kb.press(GamepadAction.Down, GamepadAction.Down, GamepadAction.Down, GamepadAction.Down)
        assertTrue(kb.col < kb.rows[kb.row].size)
    }

    @Test
    fun `masked mode is remembered`() {
        assertTrue(open(masked = true).masked)
    }
}
