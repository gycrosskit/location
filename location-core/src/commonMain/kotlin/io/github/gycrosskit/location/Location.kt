package io.github.gycrosskit.location

/**
 * 坐标保持系统原始坐标系；不做地理编码或厂商坐标转换。
 * @property latitude 纬度，角度，有效范围 -90..90。
 * @property longitude 经度，角度，有效范围 -180..180。
 * @property accuracyMeters 水平精度半径，米；负数/非有限数代表无效读数。
 * @property timestampMillis 系统定位时间，Unix epoch 毫秒；须非负且不晚于读取时间。
 */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val timestampMillis: Long)

/**
 * 单次定位的验收条件；构造时拒绝无效阈值，不自动弹出权限框。
 * @property timeoutMillis 等待期限，毫秒，1..Int.MAX_VALUE；默认 12000。
 * @property maxAgeMillis 可接受的最大缓存年龄，毫秒，非负；默认 300000，0 仅接受当前时间。
 * @property maxAccuracyMeters 可接受的最大水平误差半径，米，有限且非负；默认 500。
 */
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

    /**
     * 检查坐标、精度与时间；阈值端点包含在内，未来和无效系统读数均拒绝。
     * @param fix 待验收的原始读数。
     * @param nowMillis 读取时的 Unix epoch 毫秒，不使用单调计时。
     */
    fun accepts(fix: LocationFix, nowMillis: Long): Boolean =
        fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
            fix.accuracyMeters in 0.0..maxAccuracyMeters &&
            fix.timestampMillis >= 0 && fix.timestampMillis <= nowMillis &&
            nowMillis - fix.timestampMillis <= maxAgeMillis
}

/** 单次定位终态；协程取消抛出 CancellationException，不转换为这里的失败值。 */
sealed interface LocationResult {
    /** 验收通过的原始坐标。@property fix 满足本次 LocationOptions 的读数。 */
    data class Available(val fix: LocationFix) : LocationResult
    /** 缺少授权或等待期间授权被撤销。 */
    data object PermissionMissing : LocationResult
    /** 系统定位服务关闭。 */
    data object ServiceDisabled : LocationResult
    /** 期限内没有符合验收条件的读数。 */
    data object TimedOut : LocationResult
    /** 系统能力不可用或未分类的 SDK 失败。 */
    data object Unavailable : LocationResult
}

/** 权限由宿主提前申请；取消调用协程会释放系统定位监听。 */
interface LocationClient {
    /**
     * 优先返回合格缓存，否则等待新读数；取消/完成后释放本次系统监听。
     * @param options 本次超时与读数条件，省略时使用 LocationOptions 默认值。
     */
    suspend fun currentLocation(options: LocationOptions = LocationOptions()): LocationResult
}
