package io.github.gycrosskit.location.kuikly

import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import io.github.gycrosskit.location.LocationClient

/** SDK 每个 Renderer 创建一个实例；原生能力 owner 由宿主维护。 */
class AndroidLocationModule(client: LocationClient) : KuiklyRenderBaseModule() {
    private val handler = LocationModuleHandler(client)

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        handler.call(method, params.orEmpty()) { callback?.invoke(it) }
        return null
    }

    override fun onDestroy() {
        handler.dispose()
        super.onDestroy()
    }
}

/** 在 registerExternalModule 中注册；factory 不能复用 receiver 实例。 */
fun IKuiklyRenderExport.registerLocationModule(client: LocationClient) {
    moduleExport(LocationModule.NAME) { AndroidLocationModule(client) }
}
