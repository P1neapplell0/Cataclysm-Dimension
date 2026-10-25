# Forge 1.20.1 发布包启动修复（1.7.1 测试阶段）

## 故障与原因

2026-10-26，整合包 The Casket of Reveries2.2 的 Forge 47.4.4 / Java 17 启动日志报错：

```text
Mixin apply ... cataclysm_dimension.mixins.json:ChunkAccessMixin ... FAILED
could not find any targets matching 'markPosForPostprocessing'
No refMap loaded
```

1.7.1 的 JAR 包含 `cataclysm_dimension.mixin-refmap.json`，但 `cataclysm_dimension.mixins.json` 未通过 `refmap` 字段引用它。开发运行环境使用可读方法名，因此此前的 `runClient` 回归未暴露问题。生产环境的目标方法为 SRG 名称（这里为 `m_8113_`），没有加载引用映射时无法找到注入点，导致启动中止。

这是本模组的发布配置遗漏，不能通过清空世界、重建配置或只移除 C2ME 修复。结构预生成、重置算法及配置取值不是本次启动失败的原因。

## 修复与构建检查

- 为 Forge 的 Mixin 配置补上 `"refmap": "cataclysm_dimension.mixin-refmap.json"`。
- Forge 尚未正式发布，修复后仍使用 1.7.1，不修改 NeoForge 1.21.1 分支。回归测试期间曾临时标记为 1.7.2，下文保留该标记以对应当时的测试日志。
- 新增 `verifyMixinRefmap`，挂入 `check`，检查经过 `reobfJar` 的实际发布 JAR：配置引用存在，映射文件存在，关键注入点包含生产 SRG 映射。
- 不降低注入的 `require`，也不关闭结构回放的必要 Mixin 来绕过错误。

## 验证边界

使用整合包已经安装的 Forge 47.4.4、Java 17、Minecraft 1.20.1 客户端与运行库，在独立 `/tmp` 游戏目录直接启动 `forgeclient`，而非 Gradle 开发启动。使用离线测试身份，不使用用户的登录凭据，不打开用户存档，也不修改原整合包。

最小依赖集合保留整合包中的 Cataclysm 3.27、Curios 5.14.1、Lionfish API 2.7、C2ME Forge 0.2.0-forge.9.9.3：

- 原 1.7.1 发布包复现相同 `ChunkAccessMixin` / `No refMap loaded` 致命错误并退出。
- 1.7.2 发布包成功应用 `ChunkAccessMixin`，完成客户端纹理、音频及资源加载，没有原先的致命错误。
- 原包与修复包的运行日志分别为 `/tmp/cataclysm-production-broken.log`、`/tmp/cataclysm-production-fixed.log`。

这是生产映射下的启动回归，不代表整合包所有其他模组、全部脚本及存档玩法均已经逐项验收。此前的开发环境维度传送检查不能代替这一发布包启动检查。

## 整合包更新

1. 退出 Minecraft。
2. 将旧的 `cataclysm_dimension-forge1.20.1-1.7.1.jar` 备份到 `mods` 目录之外。
3. 放入修复后重新构建的 `cataclysm_dimension-forge1.20.1-1.7.1.jar`，替换旧测试包；不要同时保留多个测试包（包括曾临时标记为 1.7.2 的测试包）。
4. 如果专用服务器也安装本模组，两端一并更新。
5. 保留原配置和世界，不需要新建世界或删除缓存。重新启动后，如还有不同错误，再检查新的日志。
