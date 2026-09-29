package consumer
import io.github.gycrosskit.location.*
suspend fun probe(client: LocationClient) = client.currentLocation(LocationOptions(maxAccuracyMeters = 100.0))
