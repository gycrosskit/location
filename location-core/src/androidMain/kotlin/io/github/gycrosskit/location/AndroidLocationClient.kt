package io.github.gycrosskit.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android GPS/网络定位；自动切到主线程，按系统版本与授权选择 provider。
 * @param context 仅保留 applicationContext，不持有 Activity。
 */
class AndroidLocationClient(context: Context) : LocationClient {
    private val context = context.applicationContext
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override suspend fun currentLocation(options: LocationOptions): LocationResult = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) return@withContext LocationResult.PermissionMissing
        val service = manager ?: return@withContext LocationResult.Unavailable
        var activeListener: LocationListener? = null
        try {
            if (!service.isLocationServiceEnabled()) return@withContext LocationResult.ServiceDisabled
            val available = service.allProviders
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { it in available }
                // Android 11 起 GPS 也可按粗略授权返回系统模糊化位置。
                .filter { it != LocationManager.GPS_PROVIDER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
            val enabled = providers.filter { service.isProviderEnabled(it) }
            if (enabled.isEmpty()) return@withContext LocationResult.Unavailable
            enabled.mapNotNull { service.getLastKnownLocation(it) }.map { it.toFix() }
                .filter { options.accepts(it, System.currentTimeMillis()) }
                .minByOrNull { it.accuracyMeters }?.let { return@withContext LocationResult.Available(it) }

            withTimeoutOrNull(options.timeoutMillis) {
                suspendCancellableCoroutine<LocationResult> { pending ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val fix = location.toFix()
                            if (options.accepts(fix, System.currentTimeMillis()) && pending.isActive) {
                                pending.resume(LocationResult.Available(fix))
                            }
                        }
                        override fun onProviderDisabled(provider: String) {
                            if (pending.isActive) {
                                when {
                                    !hasPermission() -> pending.resume(LocationResult.PermissionMissing)
                                    !runCatching { service.isLocationServiceEnabled() }.getOrDefault(true) ->
                                        pending.resume(LocationResult.ServiceDisabled)
                                }
                            }
                        }
                        override fun onProviderEnabled(provider: String) = Unit
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }
                    activeListener = listener
                    pending.invokeOnCancellation { runCatching { service.removeUpdates(listener) } }
                    try {
                        // 注册当前关闭但可用的 provider，系统启用后会自动开始投递位置。
                        providers.forEach { service.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                    } catch (_: SecurityException) {
                        if (pending.isActive) pending.resume(LocationResult.PermissionMissing)
                    } catch (_: RuntimeException) {
                        if (pending.isActive) pending.resume(LocationResult.Unavailable)
                    }
                }
            } ?: when {
                // 等待期间权限可能被撤销且没有位置回调；与其他平台一样在 deadline 重读系统事实。
                !hasPermission() -> LocationResult.PermissionMissing
                !service.isLocationServiceEnabled() -> LocationResult.ServiceDisabled
                else -> LocationResult.TimedOut
            }
        } catch (_: SecurityException) {
            LocationResult.PermissionMissing
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            LocationResult.Unavailable
        } finally {
            activeListener?.let { runCatching { service.removeUpdates(it) } }
        }
    }

    private fun LocationManager.isLocationServiceEnabled() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLocationEnabled
        else listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).any { isProviderEnabled(it) }

    private fun hasPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun Location.toFix() = LocationFix(latitude, longitude, if (hasAccuracy()) accuracy.toDouble() else -1.0, time)
}
