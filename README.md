# GY CrossKit Location

前台单次定位，提供权限/服务状态、取消、超时、缓存时效和精度过滤。保留系统原始坐标；权限申请、地址查询、坐标转换和业务精度要求由宿主负责。

本轮 Maven/HAR 候选为 0.1.1，[prerelease 已发布](https://github.com/gycrosskit/location/releases/tag/0.1.1)，实际下载 SHA 与 JitPack 全 9 个 module 的文件引用校验通过。独立真实 JitPack Android/JVM/OHOS/iOS 编译及 Simulator 最终链接、Release HAR 独立编译通过；该段为 0.1.1 历史验收；本轮 Maven 安装版本及待验状态见下方矩阵，设备定位尚未验收。

OHPM `next` 提交已接受，仍在审核；精确版本查询及独立 Registry 安装返回 NOTFOUND。稳定 Registry `latest` 仍为 0.1.0，Release HAR 可下载不代表 Registry 可安装。

## 0.1.2 发布候选

Kuikly watchdog 以请求期限加 2 秒回执余量、构造器指定最短等待中的较大者结算，避免提前截断长请求；超时明确返回 TimedOut，空原生回执保持 Unavailable。现有 10 秒请求/12 秒桥接等待保持。

| 渠道 | 本轮版本 | 状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.2 | 待完整归档和真实远程消费 |
| HarmonyOS HAR | 0.1.1 | 原生源码未变，沿用旧 Release 已验产物 |


## 平台与要求

| 平台 | 接入方式 | 系统要求 |
| --- | --- | --- |
| Android | KMP `location-core`，`LocationManager` | API 24+ |
| iOS | KMP `location-core`，`CoreLocation` | iOS 14+（使用实例 `authorizationStatus` API） |
| HarmonyOS | 原生 `location-native` HAR，`geoLocationManager` | 当前 HAR 的 target/compatible SDK 均为 API 22 |

KMP 使用 Kotlin `2.2.21-1.0.0`、coroutines `1.10.2`。稳定 0.1.0 无 Kuikly/OHOS KMP 桥；0.1.1 候选新增 `location-kuikly` 与 core 的 `ohosArm64` 变体。不提供 Swift Package；JVM 变体只含公共 API/数据与测试逻辑，无 JVM 定位实现。

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
    implementation("com.github.gycrosskit.location:location-core:0.1.2")
}
ohosArm64Main.dependencies {
    implementation("com.github.gycrosskit.location:location-kuikly:0.1.2")
}
```

HarmonyOS 原生包独立安装，候选正式可查询后执行；发布接受与 Registry 可安装分别核验：

```sh
ohpm install @gycrosskit/location-native@0.1.1
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

## 0.1.1 候选：Kuikly 单次定位

新增 `location-kuikly` 与原生 `GycLocationModule`，每个Page各注册一个对应Module。
Maven 显式选择 `location-core:0.1.1`，OHOS 额外依赖 `location-kuikly:0.1.1`，原生 HAR 为 `@gycrosskit/location-native@0.1.1`。Maven 已完成远程文件校验，OHPM 可安装性按上方独立状态记录。

```kotlin
val location = io.github.gycrosskit.location.kuikly.LocationModule(bridgeTimeoutMillis = 12_000)
val result = location.currentLocation(LocationOptions(timeoutMillis = 10_000, maxAgeMillis = 300_000, maxAccuracyMeters = 500.0))
// pageWillDestroy: location.dispose()
```

宿主映射业务坐标/结果；协程取消、超时和dispose同时取消原生定位，旧请求ID不能停止后继请求。原生Client验证系统时间、缓存和精度；组件不弹权限申请、不转换坐标系。
`location-core` 新增OHOS变体；发布时同时核验原Android/iOS/JVM消费者，不能只验证新Kuikly模块。
