# @gycrosskit/location-native

适用版本：HAR `0.1.4`，配套 Maven `0.1.5`。完整功能与五入口限制见[功能与平台差异](https://github.com/gycrosskit/location/blob/0.1.5/docs/功能与平台差异.md)；构建、固定 Release HAR 消费与 OHPM Registry 可安装性分别见[此版发布记录](https://github.com/gycrosskit/location/releases/tag/0.1.5)。

以下精确 Registry 安装命令需该版本审核上架；审核受理与固定 Release HAR 可下载不代表 Registry 已可安装。

前台单次定位，支持超时、精度/时效过滤及取消。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

```sh
ohpm install @gycrosskit/location-native@0.1.4
```

```typescript
import { LocationClient, LocationOptions } from '@gycrosskit/location-native';
const client = new LocationClient();
const request = client.currentLocation(new LocationOptions());
const result = await request.result;
// 页面销毁时取消尚未完成的请求：
request.cancel();
```

宿主提前声明/申请 APPROXIMATELY_LOCATION，精确定位另加 LOCATION。结果状态为 available/permission_missing/service_disabled/timed_out/unavailable/cancelled；仅内存缓存，每次检查授权、服务、时效和精度。不提供后台定位、坐标转换、地址查询。原生导出 GycLocationModule，0.1.1 配合独立 Maven location-kuikly；Maven 不自动携带 HAR。

[完整接入指南](https://github.com/gycrosskit/location/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/location/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/location/releases) · [问题反馈](https://github.com/gycrosskit/location/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
