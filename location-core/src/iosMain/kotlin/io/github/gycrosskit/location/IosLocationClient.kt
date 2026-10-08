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

/** iOS CoreLocation 实现；每次请求独占 manager/delegate，自动切主线程，宿主提前申请权限。 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationClient : LocationClient {
    override suspend fun currentLocation(options: LocationOptions): LocationResult = withContext(Dispatchers.Main.immediate) {
        val manager = CLLocationManager()
        if (manager.authorizationStatus != kCLAuthorizationStatusAuthorizedAlways &&
            manager.authorizationStatus != kCLAuthorizationStatusAuthorizedWhenInUse) {
            return@withContext LocationResult.PermissionMissing
        }
        if (!CLLocationManager.locationServicesEnabled()) return@withContext LocationResult.ServiceDisabled
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
                        onFailure = { error ->
                            val failure = locationFailure(manager.authorizationStatus,
                                CLLocationManager.locationServicesEnabled(), error = error)
                            if (failure != null && pending.isActive) pending.resume(failure)
                        },
                    )
                    manager.delegate = delegate
                    manager.startUpdatingLocation()
                }
            } ?: locationFailure(
                manager.authorizationStatus,
                CLLocationManager.locationServicesEnabled(),
                fallback = LocationResult.TimedOut,
            ) ?: LocationResult.TimedOut
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
    private class Delegate(val onLocation: (CLLocation) -> Unit, val onFailure: (NSError?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            didUpdateLocations.filterIsInstance<CLLocation>().forEach(onLocation)
        }
        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) { onFailure(didFailWithError) }
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            if (manager.authorizationStatus == kCLAuthorizationStatusDenied || manager.authorizationStatus == kCLAuthorizationStatusRestricted) onFailure(null)
        }
    }
}

/** 等待期间权限与服务可发生变化；受系统策略限制与用户拒绝均缺少定位授权。 */
internal fun locationFailure(
    status: CLAuthorizationStatus,
    servicesEnabled: Boolean,
    fallback: LocationResult = LocationResult.Unavailable,
    error: NSError? = null,
): LocationResult? = when {
    status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted -> LocationResult.PermissionMissing
    !servicesEnabled -> LocationResult.ServiceDisabled
    // CoreLocation 会在暂时无法定位时报告此错误，仍可能随后交付位置；保留本次 deadline。
    error?.domain == kCLErrorDomain && error.code == kCLErrorLocationUnknown -> null
    else -> fallback
}
