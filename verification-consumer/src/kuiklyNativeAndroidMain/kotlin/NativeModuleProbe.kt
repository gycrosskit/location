import io.github.gycrosskit.location.kuikly.registerLocationModule

fun registerKuiklyLocation(exports: com.tencent.kuikly.core.render.android.IKuiklyRenderExport, client: io.github.gycrosskit.location.LocationClient) = exports.registerLocationModule(client)
