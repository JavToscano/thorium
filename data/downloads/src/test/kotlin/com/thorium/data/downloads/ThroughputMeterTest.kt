package com.thorium.data.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThroughputMeterTest {

    private val meter = ThroughputMeter()

    @Test
    fun `needs two samples before it reports a speed`() {
        assertNull(meter.record(1, 0, 0))
        assertEquals(1_000L, meter.record(1, 1_000, 1_000))
    }

    @Test
    fun `a steady transfer gives a steady speed`() {
        meter.record(1, 0, 0)
        repeat(10) { step -> meter.record(1, (step + 1) * 500_000L, (step + 1) * 1_000L) }
        assertEquals(500_000L, meter.record(1, 5_500_000, 11_000))
    }

    @Test
    fun `a sudden change is smoothed instead of jumping`() {
        meter.record(1, 0, 0)
        meter.record(1, 1_000_000, 1_000)               // 1 MB/s
        val after = meter.record(1, 1_000_000, 2_000)!! // stalled for a second
        assertTrue(after in 1..999_999, "smoothed speed was $after")
    }

    @Test
    fun `samples that are too close together are ignored`() {
        meter.record(1, 0, 0)
        meter.record(1, 1_000, 1_000)
        assertEquals(1_000L, meter.record(1, 999_999, 1_100))   // only 100 ms later: not used
    }

    @Test
    fun `a download that starts over resets its measurement`() {
        meter.record(1, 0, 0)
        meter.record(1, 5_000, 1_000)
        assertNull(meter.record(1, 100, 2_000))   // fewer bytes than before: restarted
    }

    @Test
    fun `downloads are measured independently`() {
        meter.record(1, 0, 0); meter.record(2, 0, 0)
        assertEquals(1_000L, meter.record(1, 1_000, 1_000))
        assertEquals(5_000L, meter.record(2, 5_000, 1_000))
    }

    @Test
    fun `retain forgets the rest`() {
        meter.record(1, 0, 0); meter.record(2, 0, 0)
        meter.retain(setOf(1))
        assertNull(meter.record(2, 4_000, 1_000))   // was forgotten, so it starts again
        assertEquals(1_000L, meter.record(1, 1_000, 1_000))
    }

    @Test
    fun `eta rounds up and handles unknown speed`() {
        assertEquals(10L, ThroughputMeter.etaSeconds(1_000, 100))
        assertEquals(11L, ThroughputMeter.etaSeconds(1_001, 100))
        assertNull(ThroughputMeter.etaSeconds(1_000, null))
        assertNull(ThroughputMeter.etaSeconds(1_000, 0))
        assertEquals(0L, ThroughputMeter.etaSeconds(0, 100))
    }
}
