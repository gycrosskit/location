package io.github.gycrosskit.location.kuikly

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.location.LocationClient
import io.github.gycrosskit.location.LocationOptions
import io.github.gycrosskit.location.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 每个 Renderer 独占请求表，取消只释放对应 core 定位监听。 */
class LocationModuleHandler(private val client: LocationClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = mutableMapOf<Long, Job>()

    fun call(method: String, params: String, callback: (String) -> Unit) {
        scope.launch {
            val args: JSONObject
            val id: Long
            try {
                args = JSONObject(params)
                val number = args.opt("requestId") as? Number ?: error("Missing requestId")
                id = number.toLong()
                require(id in 1..9_007_199_254_740_991L && number.toDouble() == id.toDouble())
            } catch (_: Exception) {
                callback("{\"status\":\"error\"}")
                return@launch
            }
            if (method == "cancelLocation") {
                requests.remove(id)?.cancel()
                return@launch
            }
            val options = try {
                require(method == "currentLocation" && !requests.containsKey(id))
                val timeout = args.opt("timeoutMillis") as? Number ?: error("Missing timeout")
                val age = args.opt("maxAgeMillis") as? Number ?: error("Missing maxAge")
                val accuracy = args.opt("maxAccuracyMeters") as? Number ?: error("Missing accuracy")
                require(timeout.toDouble() == timeout.toLong().toDouble())
                require(age.toDouble().isFinite() && age.toDouble() == age.toLong().toDouble())
                LocationOptions(timeout.toLong(), age.toLong(), accuracy.toDouble())
            } catch (_: Exception) {
                callback("{\"status\":\"error\"}")
                return@launch
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val response = try {
                    JSONObject().apply {
                        when (val result = client.currentLocation(options)) {
                            is LocationResult.Available -> {
                                put("status", "available")
                                put("latitude", result.fix.latitude)
                                put("longitude", result.fix.longitude)
                                put("accuracy", result.fix.accuracyMeters)
                                put("timestampMillis", result.fix.timestampMillis)
                            }
                            LocationResult.PermissionMissing -> put("status", "permission_missing")
                            LocationResult.ServiceDisabled -> put("status", "service_disabled")
                            LocationResult.TimedOut -> put("status", "timed_out")
                            LocationResult.Unavailable -> put("status", "unavailable")
                        }
                    }.toString()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "{\"status\":\"unavailable\"}"
                } finally {
                    if (requests[id] === coroutineContext[Job]) requests.remove(id)
                }
                if (isActive) callback(response)
            }
            requests[id] = job
            job.start()
        }
    }

    fun dispose() { scope.cancel() }
}
