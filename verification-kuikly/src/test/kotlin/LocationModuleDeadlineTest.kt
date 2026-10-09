package io.github.gycrosskit.location.kuikly
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.location.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import kotlin.test.*
@OptIn(ExperimentalCoroutinesApi::class)
class LocationModuleDeadlineTest {
    @Test fun `cancelled request cannot deliver to serialized successor`() = runTest {
        val module = LocationModule()
        val first = async { module.currentLocation() }
        runCurrent()
        val oldResponse = module.response
        val second = async { module.currentLocation() }
        runCurrent()
        assertEquals(1, module.calls.size)
        first.cancel()
        runCurrent()
        assertEquals(2, module.calls.size)
        assertEquals(1, module.cancelled.size)
        assertEquals(1, module.removedCallbacks)
        oldResponse(JSONObject().apply { put("status", "permission_missing") })
        runCurrent()
        assertFalse(second.isCompleted)
        module.response(JSONObject().apply { put("status", "service_disabled") })
        assertEquals(LocationResult.ServiceDisabled, second.await())
        assertEquals(2, module.cancelled.size)
        assertEquals(2, module.removedCallbacks)
        module.dispose()
    }

    @Test fun `invalid native fix never escapes as available`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation() }
        runCurrent()
        module.response(JSONObject().apply {
            put("status", "available")
            put("latitude", 91.0)
            put("longitude", 120.0)
            put("accuracy", 20.0)
            put("timestampMillis", 0L)
        })
        assertEquals(LocationResult.Unavailable, result.await())
        assertEquals(1, module.removedCallbacks)
    }

    @Test fun `thirty second request is not cut off by twelve second bridge`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation(LocationOptions(timeoutMillis = 30_000)) }
        runCurrent(); advanceTimeBy(12_001); runCurrent()
        assertFalse(result.isCompleted)
        assertTrue(module.calls.single().second.contains("\"timeoutMillis\":30000"))
        module.response(JSONObject().apply { put("status", "timed_out") })
        assertEquals(LocationResult.TimedOut, result.await())
    }
    @Test fun `watchdog waits for request deadline plus reply grace and returns timed out`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation() }; runCurrent()
        advanceTimeBy(13_999); runCurrent(); assertFalse(result.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertEquals(LocationResult.TimedOut, result.await())
        assertEquals(1, module.cancelled.size)
        assertEquals(1, module.removedCallbacks)
    }
    @Test fun `host ten second request retains twelve second bridge budget`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation(LocationOptions(timeoutMillis = 10_000)) }; runCurrent()
        advanceTimeBy(11_999); runCurrent(); assertFalse(result.isCompleted)
        advanceTimeBy(1); runCurrent(); assertEquals(LocationResult.TimedOut, result.await())
    }
    @Test fun `native empty response is unavailable rather than timeout`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation() }; runCurrent()
        module.response(null)
        assertEquals(LocationResult.Unavailable, result.await())
    }
    @Test fun `dispose after callback still cancels delivery`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation() }; runCurrent()
        module.response(JSONObject().apply { put("status", "timed_out") }); module.dispose(); runCurrent()
        assertFailsWith<CancellationException> { result.await() }
    }
}
