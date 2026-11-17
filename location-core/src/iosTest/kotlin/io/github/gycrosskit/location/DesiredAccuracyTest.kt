package io.github.gycrosskit.location

import platform.CoreLocation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesiredAccuracyTest {
    @Test fun fullAccuracyBoundsTheSystemRequestWithoutChangingAcceptance() {
        assertEquals(kCLLocationAccuracyThreeKilometers,
            desiredAccuracy(Double.MAX_VALUE, CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy))
        assertEquals(500.0, desiredAccuracy(500.0, CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy))
        assertEquals(1.0, desiredAccuracy(0.0, CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy))
        val options = LocationOptions(maxAccuracyMeters = Double.MAX_VALUE, useCachedLocation = false)
        assertTrue(options.acceptsUpdate(LocationFix(0.0, 0.0, 20_000.0, 1_000), 1_000))
    }

    @Test fun reducedAuthorizationUsesTheSystemReducedAccuracy() {
        for (threshold in listOf(0.0, 500.0, Double.MAX_VALUE)) {
            assertEquals(kCLLocationAccuracyReduced,
                desiredAccuracy(threshold, CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy))
        }
    }
}
