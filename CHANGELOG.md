# 更新日志

## 0.1.5（2026-10-08）

- OHOS先检查系统缓存；iOS CoreLocation暂态错误继续等，权限/服务变化正确分类。
- 更新功能、测试覆盖与平台差异文档；设备业务验收范围保持明确。

## 0.1.2

Kuikly watchdog 以请求期限加 2 秒回执余量、构造器指定最短等待中的较大者结算，避免提前截断长请求；超时明确返回 TimedOut，空原生回执保持 Unavailable。现有 10 秒请求/12 秒桥接等待保持。

| 渠道 | 本轮版本 | 状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.2 | JitPack 全文件/hash 与 Android/OHOS/三 iOS 编译、Simulator 链接通过 |
| HarmonyOS HAR | 0.1.1 | 原生源码未变，沿用旧 Release 已验产物 |


历史版本与验证范围见 [Releases](https://github.com/gycrosskit/location/releases)；真实消费与 Registry 状态见本轮发布验收。
