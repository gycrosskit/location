package consumer

import io.github.gycrosskit.location.LocationOptions
import io.github.gycrosskit.location.LocationResult
import io.github.gycrosskit.location.kuikly.LocationModule

fun createOhos() = LocationModule(bridgeTimeoutMillis = 12_000)
suspend fun locate(module: LocationModule): LocationResult = module.currentLocation(LocationOptions())
fun destroy(module: LocationModule) = module.dispose()
