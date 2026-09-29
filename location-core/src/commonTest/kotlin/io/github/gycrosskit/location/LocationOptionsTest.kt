package io.github.gycrosskit.location

import kotlin.test.*

class LocationOptionsTest {
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
