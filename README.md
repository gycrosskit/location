# GY CrossKit Location

前台单次定位，提供权限/服务状态、取消、超时、缓存时效和精度过滤。保留系统原始坐标；权限申请、地址查询、坐标转换和业务精度要求由宿主负责。

## 平台与要求

| 平台 | 接入方式 | 系统要求 |
| --- | --- | --- |
| Android | KMP `location-core`，`LocationManager` | API 24+ |
| iOS | KMP `location-core`，`CoreLocation` | iOS 14+（使用实例 `authorizationStatus` API） |
| HarmonyOS | 原生 `location-native` HAR，`geoLocationManager` | 当前 HAR 的 target/compatible SDK 均为 API 22 |

KMP 使用 Kotlin `2.2.21-1.0.0`、coroutines `1.10.2`。本版本无 Swift Package、Kuikly Module 或 KMP `ohosArm64` 桥；JVM 变体只含公共 API/数据与测试逻辑，无 JVM 定位实现。

## 安装

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}
```

```kotlin
commonMain.dependencies {
    implementation("com.github.gycrosskit.location:location-core:0.1.0")
}
```

HarmonyOS 原生包独立安装：

```sh
ohpm install @gycrosskit/location-native@0.1.0
```

## 最小使用

```kotlin
import io.github.gycrosskit.location.*

val client: LocationClient = AndroidLocationClient(context)
// iOS 平台入口用 IosLocationClient()。
// 先取得权限，再在页面生命周期绑定的协程中调用：
val result = client.currentLocation(LocationOptions(
    timeoutMillis = 12_000,
    maxAgeMillis = 300_000,
    maxAccuracyMeters = 100.0,
))
when (result) {
    is LocationResult.Available -> {
        val latitude = result.fix.latitude
        val longitude = result.fix.longitude
        // 交给宿主使用，fix 还包含精度和 Unix 毫秒时间。
    }
    LocationResult.PermissionMissing -> { /* 申请权限或展示引导 */ }
    LocationResult.ServiceDisabled -> { /* 展示系统定位服务引导 */ }
    LocationResult.TimedOut, LocationResult.Unavailable -> { /* 提示重试 */ }
}
```

HarmonyOS 的 `LocationClient.currentLocation` 返回带 `result` Promise 和 `cancel()` 的请求，见接入指南。

## 权限与生命周期

Android 声明并取得 `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`；iOS 填写 `NSLocationWhenInUseUsageDescription` 并提前授权；HarmonyOS 申请 `ohos.permission.APPROXIMATELY_LOCATION`，精确定位另加 `ohos.permission.LOCATION`。

库不将粗略授权直接判失败，位置满足 `maxAccuracyMeters` 才返回成功；拒绝非法经纬度、未来/过期坐标和不合格精度。KMP 请求协程取消会清理独占系统监听，保留取消语义；完成和超时同样清理。iOS 平台操作内部切换主线程。Android 同时监听已启用的 GPS/网络 Provider，接受第一份合格位置。

HarmonyOS 页面销毁时调用请求 `cancel()`；每个 client 仅内存缓存上次成功位置，每次读取重新检查权限、服务、时效和精度，不持久化位置。不提供后台或持续定位。GPS、室内/室外、权限和后台生命周期需真实设备验收。

## 文档与帮助

- [接入指南](docs/接入指南.md)：平台初始化、权限声明和生命周期。
- [开发与验证](docs/开发与验证.md)：源码构建、检查命令与验收范围。
- [版本与发行说明](https://github.com/gycrosskit/location/releases)、[问题反馈](https://github.com/gycrosskit/location/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
