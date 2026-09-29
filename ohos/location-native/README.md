# GY CrossKit Location

Android、iOS、HarmonyOS 的前台单次定位。提供权限与服务状态、取消、超时、缓存时效和精度过滤；不申请权限、不转换坐标、不查询地址。业务精度要求和地址展示由宿主决定。

## 平台与目录

| 目录 | 实现与要求 |
| --- | --- |
| `location-core/` | KMP 公共 API；Android 24+ 的 LocationManager；iOS 14+ 的 CoreLocation |
| `ohos/location-native/` | HarmonyOS 原生 HAR；API 22 SDK 构建，`geoLocationManager` |
| `verification-consumer/` | 独立 Maven 消费工程，默认只从 JitPack 获取本组件 |

鸿蒙直接从 ArkTS 调用 HAR。本版本不提供 Kuikly Module 或 KMP `ohosArm64` 桥接。JVM 变体仅包含公共数据/API，便于运行契约测试，不包含 JVM 定位实现。依赖 Kotlin 2.2.21-1.0.0、kotlinx-coroutines 1.10.2；许可证 Apache-2.0。

## Android / iOS 接入

```kotlin
repositories { maven("https://jitpack.io") }
commonMain.dependencies {
    implementation("com.github.gycrosskit.location:location-core:0.1.0")
}
```

Android 创建 `AndroidLocationClient(context)`；iOS 创建 `IosLocationClient()`；两者均实现 `LocationClient`：

```kotlin
val result = client.currentLocation(LocationOptions(
    timeoutMillis = 12_000,
    maxAgeMillis = 300_000,
    maxAccuracyMeters = 100.0,
))
when (result) {
    is LocationResult.Available -> useCoordinate(result.fix.latitude, result.fix.longitude)
    LocationResult.PermissionMissing -> showPermissionHelp()
    LocationResult.ServiceDisabled -> showLocationSettingsHelp()
    LocationResult.TimedOut, LocationResult.Unavailable -> showRetry()
}
```

宿主先声明并取得前台定位权限：Android 的 `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`；iOS 的 `NSLocationWhenInUseUsageDescription`。库不会把粗略授权直接判失败；只有返回位置满足 `maxAccuracyMeters` 才成功。坐标带精度与 Unix 毫秒时间，未来坐标、过期坐标和非法经纬度均拒绝。

绑定页面/宿主生命周期的协程负责取消。每个请求独占系统监听；完成、超时或取消时清理。iOS 平台操作在主线程执行。Android 同时监听已启用的 GPS/网络 Provider，接受第一份合格位置。无合格结果返回 `TimedOut`，外部协程取消保留取消语义。

## HarmonyOS 接入

```sh
ohpm install @gycrosskit/location-native@0.1.0
```

目标包 `@gycrosskit/location-native:0.1.0`；以 ohpm 查询与安装成功为上架依据。HAR 发布状态见 Release。开发阶段也可安装 `ohos/location-native/build/default/outputs/default/LocationNative.har`。

```typescript
import { LocationClient, LocationOptions } from '@gycrosskit/location-native';
const client = new LocationClient();
const options = new LocationOptions();
options.maxAccuracyMeters = 100;
const request = client.currentLocation(options);
const result = await request.result;
// 页面销毁时执行；取消后 result.status 为 cancelled。
request.cancel();
```

宿主声明并申请 `ohos.permission.APPROXIMATELY_LOCATION`，精确定位另加 `ohos.permission.LOCATION`。结果 `status` 为 `available`、`permission_missing`、`service_disabled`、`timed_out`、`unavailable`、`cancelled`；成功时 `fix` 与 KMP 同字段。一个 `LocationClient` 仅在内存缓存上次成功位置，每次读取仍重新检查权限、服务、时效和精度；不持久化位置。

## 验证与发布

```sh
ANDROID_HOME="$ANDROID_HOME" bash gradlew :location-core:jvmTest :location-core:compileDebugKotlinAndroid :location-core:compileKotlinIosSimulatorArm64
node ohos/tests/location.test.cjs
cd ohos && DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw assembleHar --no-daemon
```

HAR 契约检查可通过 `TYPESCRIPT_PATH` 指向已有 TypeScript 模块，不需要测试框架。检查权限、服务、取消幂等、超时、陈旧/精度不合格位置和缓存命中。`location-core` JVM 测试覆盖时间、精度与经纬度边界。编译与契约检查不代表真机验收；权限弹窗、GPS 室内/室外与后台生命周期仍需宿主真机验证。

发布使用 macOS 生成的版本化 Maven 归档（`publishAllPublicationsToStagingRepository`）；JitPack 校验 SHA-256 后安装，确保 iOS KLIB 可下载。标签不可覆盖。独立远程消费验证：

```sh
bash gradlew -p verification-consumer compileDebugKotlinAndroid compileKotlinIosArm64 compileKotlinIosX64 linkDebugFrameworkIosSimulatorArm64
```

参考 [Android LocationManager](https://developer.android.com/reference/android/location/LocationManager)、[Apple CoreLocation](https://developer.apple.com/documentation/corelocation)、[OpenHarmony LocationKit](https://gitee.com/openharmony/docs/blob/master/zh-cn/application-dev/reference/apis-location-kit/js-apis-geoLocationManager.md)。
