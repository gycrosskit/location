package consumer
import android.content.Context
import io.github.gycrosskit.location.AndroidLocationClient
fun create(context: Context) = AndroidLocationClient(context)
