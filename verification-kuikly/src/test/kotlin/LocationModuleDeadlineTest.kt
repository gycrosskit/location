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
    @Test fun `thirty second request is not cut off by twelve second bridge`() = runTest {
        val module = LocationModule()
        val result = async { module.currentLocation(LocationOptions(timeoutMillis = 30_000)) }
        runCurrent(); advanceTimeBy(12_001); runCurrent()
        assertFalse(result.isCompleted)
        assertTrue(module.calls.single().second.contains("timeoutMillis=30000"))
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
