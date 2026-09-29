package io.github.gycrosskit.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.*
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class IosLocationClient : LocationClient {
    override suspend fun currentLocation(options: LocationOptions): LocationResult = withContext(Dispatchers.Main.immediate) {
        if (!CLLocationManager.locationServicesEnabled()) return@withContext LocationResult.ServiceDisabled
        val manager = CLLocationManager()
        if (manager.authorizationStatus != kCLAuthorizationStatusAuthorizedAlways &&
            manager.authorizationStatus != kCLAuthorizationStatusAuthorizedWhenInUse) {
            return@withContext LocationResult.PermissionMissing
        }
        manager.location?.toFix()?.takeIf { options.accepts(it, now()) }?.let {
            return@withContext LocationResult.Available(it)
        }
        // 每次请求独占 manager/delegate，取消后的迟到回调不会恢复下一次请求。
        var delegate: Delegate? = null
        try {
            manager.desiredAccuracy = options.maxAccuracyMeters.coerceAtLeast(1.0)
            withTimeoutOrNull(options.timeoutMillis) {
                suspendCancellableCoroutine<LocationResult> { pending ->
                    delegate = Delegate(
                        onLocation = { location ->
                            val fix = location.toFix()
                            if (options.accepts(fix, now()) && pending.isActive) pending.resume(LocationResult.Available(fix))
                        },
                        onFailure = {
                            if (pending.isActive) pending.resume(
                                if (manager.authorizationStatus == kCLAuthorizationStatusDenied) LocationResult.PermissionMissing
                                else if (!CLLocationManager.locationServicesEnabled()) LocationResult.ServiceDisabled
                                else LocationResult.Unavailable
                            )
                        },
                    )
                    manager.delegate = delegate
                    manager.startUpdatingLocation()
                }
            } ?: LocationResult.TimedOut
        } finally {
            manager.stopUpdatingLocation()
            manager.delegate = null
            delegate = null
        }
    }

    private fun now() = (NSDate().timeIntervalSince1970 * 1000).toLong()
    private fun CLLocation.toFix() = coordinate.useContents {
        LocationFix(latitude, longitude, horizontalAccuracy, (timestamp.timeIntervalSince1970 * 1000).toLong())
    }
    private class Delegate(val onLocation: (CLLocation) -> Unit, val onFailure: () -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            didUpdateLocations.filterIsInstance<CLLocation>().forEach(onLocation)
        }
        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) { onFailure() }
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            if (manager.authorizationStatus == kCLAuthorizationStatusDenied || manager.authorizationStatus == kCLAuthorizationStatusRestricted) onFailure()
        }
    }
}
