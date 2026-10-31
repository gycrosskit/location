package io.github.gycrosskit.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class AndroidLocationProviderTest {
    private lateinit var manager: ShadowLocationManager
    private lateinit var client: AndroidLocationClient

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        manager = shadowOf(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        manager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        manager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        client = AndroidLocationClient(context)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    @Config(sdk = [30, 31])
    fun coarseGpsOnlyAcceptsQualifiedApproximateCache() = runTest {
        val location = fix()
        manager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location)
        assertEquals(LocationResult.Available(location.toFix()), client.currentLocation(options()))
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    @Test
    @Config(sdk = [24, 29])
    fun legacyCoarseGpsOnlyKeepsUnavailableInsteadOfRequestingFineProvider() = runTest {
        manager.setLastKnownLocation(LocationManager.GPS_PROVIDER, fix())
        assertEquals(LocationResult.Unavailable, client.currentLocation(options()))
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    @Test fun coarseGpsOnlyFiltersCacheAndNewFixesByAccuracyAndTime() = runTest {
        manager.setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(accuracyMeters = 5_000f))
        val result = async { client.currentLocation(options()) }
        runCurrent()
        assertFalse(result.isCompleted)
        for (location in listOf(
            fix(accuracyMeters = 5_000f),
            fix(timestamp = System.currentTimeMillis() - 10_000),
            fix(timestamp = System.currentTimeMillis() + 10_000),
        )) {
            manager.simulateLocation(location)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertFalse(result.isCompleted)
        }
        val accepted = fix()
        manager.simulateLocation(accepted)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(LocationResult.Available(accepted.toFix()), result.await())
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    @Test fun enabledGpsReplacesNetworkDuringRequest() = runTest {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        manager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        manager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        val result = async { client.currentLocation(options()) }
        runCurrent()
        assertTrue(manager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isNotEmpty())
        manager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        manager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertFalse(result.isCompleted)
        val accepted = fix()
        manager.simulateLocation(accepted)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(LocationResult.Available(accepted.toFix()), result.await())
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    @Test fun providerSwitchWithoutFixStillTimesOut() = runTest {
        manager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        manager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        val result = async { client.currentLocation(options()) }
        runCurrent()
        manager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        manager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(LocationResult.TimedOut, result.await())
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    @Test fun systemLocationDisabledDuringRequestFinishesAndCleansListeners() = runTest {
        val result = async { client.currentLocation(options()) }
        runCurrent()
        manager.setLocationEnabled(false)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(LocationResult.ServiceDisabled, result.await())
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    @Test fun cancellationReleasesBothEnabledAndDisabledProviders() = runTest {
        val result = async { client.currentLocation(options()) }
        runCurrent()
        assertTrue(manager.getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isNotEmpty())
        result.cancel()
        runCurrent()
        assertTrue(result.isCancelled)
        assertTrue(manager.getLocationUpdateListeners().isEmpty())
    }

    private fun options() = LocationOptions(timeoutMillis = 100, maxAgeMillis = 1_000, maxAccuracyMeters = 3_000.0)
    private fun fix(accuracyMeters: Float = 2_000f, timestamp: Long = System.currentTimeMillis()) =
        Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 31.0
            longitude = 121.0
            accuracy = accuracyMeters
            time = timestamp
            elapsedRealtimeNanos = 1L
        }
    private fun Location.toFix() = LocationFix(latitude, longitude, accuracy.toDouble(), time)
}
