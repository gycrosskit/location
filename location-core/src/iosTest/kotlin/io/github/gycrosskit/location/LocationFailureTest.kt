package io.github.gycrosskit.location

import platform.CoreLocation.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationFailureTest {
    @Test fun waitingAuthorizationChangesPreserveFailureCategory() {
        assertEquals(LocationResult.PermissionMissing, locationFailure(kCLAuthorizationStatusRestricted, true))
        assertEquals(LocationResult.PermissionMissing, locationFailure(kCLAuthorizationStatusDenied, true))
        assertEquals(LocationResult.PermissionMissing, locationFailure(kCLAuthorizationStatusRestricted, false))
        assertEquals(LocationResult.ServiceDisabled, locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, false))
        assertEquals(LocationResult.Unavailable, locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, true))
        assertEquals(LocationResult.PermissionMissing, locationFailure(kCLAuthorizationStatusDenied, true, LocationResult.TimedOut))
        assertEquals(LocationResult.ServiceDisabled, locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, false, LocationResult.TimedOut))
        assertEquals(LocationResult.TimedOut, locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, true, LocationResult.TimedOut))
    }
}
