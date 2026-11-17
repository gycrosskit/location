# @gycrosskit/location-native

> 当前版本 `1.0.2`：Maven/JitPack、Git Pod `native-1.0.2`、GitHub HAR `har-1.0.2` 与私 OHPM 已发布。Maven/Git Pod 消费编译及私 OHPM 的精确安装、锁文件和字节校验已通过；设备行为见验证范围。

本模块 `1.0.3` 为本次发布候选，HarmonyOS 定位实现未变，发布与精确消费另行验收。

固定 [HAR 1.0.0 Release](https://github.com/gycrosskit/location/releases/tag/har-1.0.0) 保留原字节；以下 Registry 命令须独立核验精确版本是否可安装，Release 附件不代表 Registry 已上架。

前台单次定位，支持超时、精度/时效过滤及取消。当前 HAR target/compatible SDK 为 HarmonyOS API 22。

先使用调用方现有的私仓只读 scope 配置，再安装精确版本：

```sh
ohpm install @gycrosskit/location-native@1.0.3
```

```typescript
import { LocationClient, LocationOptions } from '@gycrosskit/location-native';
const client = new LocationClient();
const request = client.currentLocation(new LocationOptions());
const result = await request.result;
// 页面销毁时取消尚未完成的请求：
request.cancel();
```

宿主提前声明/申请 APPROXIMATELY_LOCATION，精确定位另加 LOCATION。结果状态为 available/permission_missing/service_disabled/timed_out/unavailable/cancelled；仅内存缓存，每次检查授权和服务；`useCachedLocation` 默认开启缓存，设为 `false` 时跳过实例及系统缓存；缓存须符合 `maxAgeMillis` 和精度，新监听回调检查坐标、精度及非负、非未来时间戳，不受缓存年龄限制。不提供后台定位、坐标转换、地址查询。原生导出 GycLocationModule，配合独立 Maven location-kuikly；Maven 不自动携带 HAR。

[完整接入指南](https://github.com/gycrosskit/location/blob/main/docs/接入指南.md) · [开发与验证](https://github.com/gycrosskit/location/blob/main/docs/开发与验证.md) · [版本](https://github.com/gycrosskit/location/releases) · [问题反馈](https://github.com/gycrosskit/location/issues)。

Apache-2.0，见 [LICENSE](LICENSE)。
