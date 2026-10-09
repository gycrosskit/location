package io.github.gycrosskit.location.kuikly

import io.github.gycrosskit.location.IosLocationClient

/** 从宿主既有 Shared framework 导出，供原生 Kuikly receiver 调用；不创建额外 runtime。 */
class IosLocationModuleHandler() {
    private val handler = LocationModuleHandler(IosLocationClient())
    fun call(method: String, params: String, callback: (String) -> Unit) = handler.call(method, params, callback)
    fun dispose() = handler.dispose()
}
