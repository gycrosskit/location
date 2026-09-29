package io.github.gycrosskit.location

/** 坐标保持系统原始坐标系；不做地理编码或厂商坐标转换。 */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val timestampMillis: Long)

data class LocationOptions(
    val timeoutMillis: Long = 12_000,
    val maxAgeMillis: Long = 300_000,
    val maxAccuracyMeters: Double = 500.0,
) {
    init {
        require(timeoutMillis in 1..Int.MAX_VALUE.toLong())
        require(maxAgeMillis >= 0)
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters >= 0)
    }

    fun accepts(fix: LocationFix, nowMillis: Long): Boolean =
        fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
            fix.accuracyMeters in 0.0..maxAccuracyMeters &&
            fix.timestampMillis >= 0 && fix.timestampMillis <= nowMillis &&
            nowMillis - fix.timestampMillis <= maxAgeMillis
}

sealed interface LocationResult {
    data class Available(val fix: LocationFix) : LocationResult
    data object PermissionMissing : LocationResult
    data object ServiceDisabled : LocationResult
    data object TimedOut : LocationResult
    data object Unavailable : LocationResult
}

/** 权限由宿主提前申请；取消调用协程会释放系统定位监听。 */
interface LocationClient {
    suspend fun currentLocation(options: LocationOptions = LocationOptions()): LocationResult
}
