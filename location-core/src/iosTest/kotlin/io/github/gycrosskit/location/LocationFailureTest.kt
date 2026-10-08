package io.github.gycrosskit.location

import platform.CoreLocation.*
import platform.Foundation.NSError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationFailureTest {
    @Test fun temporaryUnknownLocationKeepsWaitingButDoesNotHidePermissionOrServiceLoss() {
        val temporary = NSError.errorWithDomain(kCLErrorDomain, kCLErrorLocationUnknown, null)
        assertNull(locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, true, error = temporary))
        assertEquals(LocationResult.PermissionMissing,
            locationFailure(kCLAuthorizationStatusDenied, true, error = temporary))
        assertEquals(LocationResult.ServiceDisabled,
            locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, false, error = temporary))
        assertEquals(LocationResult.Unavailable,
            locationFailure(kCLAuthorizationStatusAuthorizedWhenInUse, true,
                error = NSError.errorWithDomain("other", kCLErrorLocationUnknown, null)))
    }
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
