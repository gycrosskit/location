package io.github.gycrosskit.location.kuikly

import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.location.LocationClient
import io.github.gycrosskit.location.LocationFix
import io.github.gycrosskit.location.LocationOptions
import io.github.gycrosskit.location.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 每个 Page 注册一个实例；取消时停止同一原生请求，不只释放 Kotlin 回调。 */
class LocationModule(private val bridgeTimeoutMillis: Long = 12_000L) : Module(), LocationClient {
    private val mutex = Mutex()
    private var disposed = false
    private var nextRequestId = 0L
    private var activeRequestId: Long? = null
    private val pending = mutableSetOf<CancellableContinuation<JSONObject?>>()
    init { require(bridgeTimeoutMillis > 0) }
    override fun moduleName(): String = NAME

    @OptIn(ExperimentalTime::class)
    override suspend fun currentLocation(options: LocationOptions): LocationResult = mutex.withLock {
        check(!disposed) { "LocationModule is disposed" }
        val requestId = ++nextRequestId
        activeRequestId = requestId
        val args = JSONObject().apply {
            put("requestId", requestId)
            put("timeoutMillis", options.timeoutMillis)
            put("maxAgeMillis", options.maxAgeMillis)
            put("maxAccuracyMeters", options.maxAccuracyMeters)
        }
        val response = try {
            withTimeoutOrNull(bridgeTimeoutMillis) { await(args) }
        } finally {
            cancelNative(requestId)
            if (activeRequestId == requestId) activeRequestId = null
        }
        // resume 后结果可能仍在协程队列；dispose 必须拦截已完成回调的迟交付。
        if (disposed) throw CancellationException("LocationModule is disposed")
        when (response?.optString("status")) {
            "permission_missing" -> LocationResult.PermissionMissing
            "service_disabled" -> LocationResult.ServiceDisabled
            "timed_out" -> LocationResult.TimedOut
            "available" -> {
                val fix = LocationFix(response.optDouble("latitude", Double.NaN),
                    response.optDouble("longitude", Double.NaN), response.optDouble("accuracy", Double.NaN),
                    response.optLong("timestampMillis", -1L))
                if (options.accepts(fix, Clock.System.now().toEpochMilliseconds())) LocationResult.Available(fix)
                else LocationResult.Unavailable
            }
            else -> LocationResult.Unavailable
        }
    }

    private suspend fun await(args: JSONObject): JSONObject? {
        var reference: CallbackRef? = null
        var continuation: CancellableContinuation<JSONObject?>? = null
        try {
            return suspendCancellableCoroutine { result ->
                continuation = result
                pending += result
                reference = toNative(false, "currentLocation", args.toString(), { response ->
                    if (result.isActive) result.resume(response)
                }, false).callbackRef
            }
        } finally {
            continuation?.let { pending.remove(it) }
            reference?.let(::removeCallback)
        }
    }

    private fun cancelNative(requestId: Long) {
        asyncToNativeMethod("cancelLocation", JSONObject().apply { put("requestId", requestId) }, null)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        activeRequestId?.let(::cancelNative)
        pending.toList().forEach { it.cancel() }
        pending.clear()
    }
    companion object { const val NAME = "GycLocationModule" }
}
