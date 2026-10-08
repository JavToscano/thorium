package com.thorium.data.downloads

/**
 * Turns the byte counts a download reports over time into a steady speed. The raw rate jumps with
 * every chunk, so it is smoothed (exponential average) to read well on screen.
 */
class ThroughputMeter(
    /** How much of each new measurement counts, 0..1. Lower is smoother. */
    private val smoothing: Double = 0.3,
    /** Measurements closer together than this are ignored: too noisy to be meaningful. */
    private val minIntervalMs: Long = 400,
) {
    private class Sample(val bytes: Long, val at: Long, val speed: Double)

    private val samples = HashMap<Long, Sample>()

    /** @return bytes per second for [id], or null while there is not enough data yet. */
    fun record(id: Long, bytes: Long, now: Long): Long? {
        val previous = samples[id]
        // First sight of this download, or it started over from zero: begin a new measurement.
        if (previous == null || bytes < previous.bytes) {
            samples[id] = Sample(bytes, now, 0.0)
            return null
        }
        val elapsed = now - previous.at
        if (elapsed < minIntervalMs) return previous.speed.takeIf { it > 0 }?.toLong()
        val instant = (bytes - previous.bytes) * 1000.0 / elapsed
        val speed = if (previous.speed == 0.0) instant else previous.speed * (1 - smoothing) + instant * smoothing
        samples[id] = Sample(bytes, now, speed)
        return speed.toLong()
    }

    /** Forgets every download not in [ids] (finished, paused or removed ones). */
    fun retain(ids: Set<Long>) {
        samples.keys.retainAll(ids)
    }

    companion object {
        /** Seconds left at [bytesPerSecond], or null when the speed is unknown or zero. */
        fun etaSeconds(remainingBytes: Long, bytesPerSecond: Long?): Long? {
            if (bytesPerSecond == null || bytesPerSecond <= 0 || remainingBytes < 0) return null
            return (remainingBytes + bytesPerSecond - 1) / bytesPerSecond
        }
    }
}
