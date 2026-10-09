package io.github.gycrosskit.location.kuikly

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocationModuleHandlerTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Client : LocationClient {
        val waits = mutableListOf<CompletableDeferred<LocationResult>>()
        val cancelled = mutableListOf<Int>()
        override suspend fun currentLocation(options: LocationOptions): LocationResult {
            val index = waits.size
            val wait = CompletableDeferred<LocationResult>()
            waits += wait
            try { return wait.await() } finally { cancelled += index }
        }
    }
    private fun args(id: Int) = "{\"requestId\":$id,\"timeoutMillis\":30000,\"maxAgeMillis\":300000,\"maxAccuracyMeters\":500}"

    @Test fun `cancel releases only matching listener and late old result cannot complete next`() = runTest {
        val client = Client()
        val handler = LocationModuleHandler(client)
        val replies = mutableListOf<String>()
        handler.call("currentLocation", args(1), replies::add)
        handler.call("currentLocation", args(2), replies::add)
        runCurrent()
        handler.call("cancelLocation", "{\"requestId\":1}", replies::add)
        runCurrent()
        assertEquals(listOf(0), client.cancelled)
        client.waits[0].complete(LocationResult.PermissionMissing)
        client.waits[1].complete(LocationResult.ServiceDisabled)
        runCurrent()
        assertEquals(listOf("service_disabled"), replies.map { JSONObject(it).optString("status") })
        handler.dispose()
    }

    @Test fun `dispose suppresses pending terminal and new renderer owns independent same ID`() = runTest {
        val client = Client()
        val old = LocationModuleHandler(client)
        val fresh = LocationModuleHandler(client)
        val replies = mutableListOf<String>()
        old.call("currentLocation", args(1), replies::add)
        runCurrent()
        old.dispose()
        fresh.call("currentLocation", args(1), replies::add)
        runCurrent()
        client.waits[0].complete(LocationResult.TimedOut)
        client.waits[1].complete(LocationResult.Available(LocationFix(30.0, 120.0, 10.0, 1234)))
        runCurrent()
        val response = JSONObject(replies.single())
        assertEquals("available", response.optString("status"))
        assertEquals(10.0, response.optDouble("accuracy", -1.0))
        assertEquals(1234, response.optLong("timestampMillis", -1))
        fresh.dispose()
    }

    @Test fun `fractional ID and invalid options fail before starting location`() = runTest {
        val client = Client()
        val handler = LocationModuleHandler(client)
        val replies = mutableListOf<String>()
        handler.call("currentLocation", args(1).replace("\"requestId\":1", "\"requestId\":1.5"), replies::add)
        handler.call("currentLocation", args(2).replace("30000", "0"), replies::add)
        runCurrent()
        assertEquals(2, replies.size)
        assertTrue(replies.all { JSONObject(it).optString("status") == "error" })
        assertTrue(client.waits.isEmpty())
        handler.dispose()
    }
}
