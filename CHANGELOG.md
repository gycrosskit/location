# 更新日志

## 0.1.2

Kuikly watchdog 以请求期限加 2 秒回执余量、构造器指定最短等待中的较大者结算，避免提前截断长请求；超时明确返回 TimedOut，空原生回执保持 Unavailable。现有 10 秒请求/12 秒桥接等待保持。

| 渠道 | 本轮版本 | 状态 |
| --- | --- | --- |
| Maven core/Kuikly | 0.1.2 | 待完整归档和真实远程消费 |
| HarmonyOS HAR | 0.1.1 | 原生源码未变，沿用旧 Release 已验产物 |


历史版本与验证范围见 [Releases](https://github.com/gycrosskit/location/releases)；候选状态在完成本轮验证后更新。
