package io.github.gycrosskit.location

import kotlin.test.*

class LocationOptionsTest {
    @Test fun validatesAllOptionBoundsBeforeStartingNativeLocation() {
        for (timeout in listOf(-1L, 0L, Int.MAX_VALUE.toLong() + 1)) {
            assertFailsWith<IllegalArgumentException> { LocationOptions(timeoutMillis = timeout) }
        }
        assertFailsWith<IllegalArgumentException> { LocationOptions(maxAgeMillis = -1) }
        for (accuracy in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { LocationOptions(maxAccuracyMeters = accuracy) }
        }
        val strict = LocationOptions(timeoutMillis = Int.MAX_VALUE.toLong(), maxAgeMillis = 0, maxAccuracyMeters = 0.0)
        val fix = LocationFix(-90.0, 180.0, 0.0, Long.MAX_VALUE)
        assertTrue(strict.accepts(fix, Long.MAX_VALUE))
        assertFalse(strict.accepts(fix.copy(timestampMillis = Long.MAX_VALUE - 1), Long.MAX_VALUE))
        assertFalse(strict.accepts(fix.copy(timestampMillis = -1), Long.MAX_VALUE))
        assertFalse(strict.accepts(fix.copy(accuracyMeters = Double.NaN), Long.MAX_VALUE))
        assertFalse(strict.accepts(fix.copy(longitude = Double.NEGATIVE_INFINITY), Long.MAX_VALUE))
        assertTrue(LocationOptions(maxAgeMillis = Long.MAX_VALUE).accepts(fix.copy(timestampMillis = 0), Long.MAX_VALUE))
    }

    @Test fun rejectsStaleFutureInaccurateAndInvalidCoordinates() {
        val options = LocationOptions(maxAgeMillis = 100, maxAccuracyMeters = 20.0)
        val fix = LocationFix(30.0, 120.0, 20.0, 900)
        assertTrue(options.accepts(fix, 1000))
        assertFalse(options.accepts(fix.copy(timestampMillis = 899), 1000))
        assertFalse(options.accepts(fix.copy(timestampMillis = 1001), 1000))
        assertFalse(options.accepts(fix.copy(accuracyMeters = 20.1), 1000))
        assertFalse(options.accepts(fix.copy(accuracyMeters = -1.0), 1000))
        assertFalse(options.accepts(fix.copy(latitude = Double.NaN), 1000))
        assertFalse(options.accepts(fix.copy(longitude = 181.0), 1000))
        assertFailsWith<IllegalArgumentException> { LocationOptions(timeoutMillis = 0) }
    }
}
