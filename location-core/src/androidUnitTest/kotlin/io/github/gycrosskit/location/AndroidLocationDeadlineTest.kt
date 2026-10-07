package io.github.gycrosskit.location

import android.Manifest
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AndroidLocationDeadlineTest {
    @Test fun deadlineReadsCurrentPermissionInsteadOfMisreportingTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val application = shadowOf(context)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val client = AndroidLocationClient(context)
        try {
            val revoked = async { client.currentLocation(LocationOptions(timeoutMillis = 100)) }
            runCurrent()
            application.denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            advanceTimeBy(100); runCurrent()
            assertEquals(LocationResult.PermissionMissing, revoked.await())

            application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            val timeout = async { client.currentLocation(LocationOptions(timeoutMillis = 100)) }
            runCurrent(); advanceTimeBy(100); runCurrent()
            assertEquals(LocationResult.TimedOut, timeout.await())
        } finally { Dispatchers.resetMain() }
    }
}
