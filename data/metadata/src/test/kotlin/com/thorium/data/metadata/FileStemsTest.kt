package com.thorium.data.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileStemsTest {
    @Test
    fun `removes a plain extension`() {
        assertEquals("Super Mario Advance (USA, Europe)", FileStems.of("Super Mario Advance (USA, Europe).gba"))
    }

    @Test
    fun `removes an archive extension and the one inside it`() {
        assertEquals("Game (USA)", FileStems.of("Game (USA).gba.zip"))
        assertEquals("Game (USA)", FileStems.of("Game (USA).zip"))
    }

    @Test
    fun `keeps a dot that belongs to the title`() {
        assertEquals("Dr. Mario (USA)", FileStems.of("Dr. Mario (USA)"))
        assertEquals("Dr. Mario (USA)", FileStems.of("Dr. Mario (USA).nes"))
    }

    @Test
    fun `a name without extension is unchanged`() {
        assertEquals("Tetris", FileStems.of("Tetris"))
    }
}
