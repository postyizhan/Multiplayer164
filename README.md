# Multiplayer164 - 局域网联机增强模组

Multiplayer164 是一个专为 Minecraft 1.6.4 版本设计的 Forge 模组，旨在优化和增强原版的局域网联机体验。

本模组允许您在开放局域网时指定固定的端口号，控制 PVP 开关，指定游戏模式和是否允许作弊，并且会自动保存设置项。

保存的设置位于配置文件 `游戏目录/config/Multiplayer164.cfg`

![](banner.png)

---

## 开发环境

工具链:ForgeGradle 7.0.28 + Gradle 9.5 + Forge 9.11.1.960,构建栈与 [MC1.6.4-Forge-Template](https://github.com/postyizhan/MC1.6.4-Forge-Template) 保持一致。开箱即可在现代 JDK(Java 8u20 及以上)上跑 `runClient`/`runServer`,无需手动放入 LegacyJavaFixer。

构建:

```bash
./gradlew build
```

产物在 `build/libs/`,有两个:`Multiplayer164-<version>.jar` 是 MCP 名的 dev 产物,`Multiplayer164-<version>-srg.jar` 是 renamer 重映射后的版本——**发布给玩家的是后者**。

需要本机装有 JDK 8(toolchain 目标);未装时 Gradle 会自动下载。

**首次运行前必做**——下载游戏资产:

```bash
./gradlew downloadGameAssets
```

1.6.4 的非英语语言文件与音效不在客户端 jar 里。dev 运行入口 slime-launcher 把资产目录固定解析为系统默认 `%APPDATA%\.minecraft\assets`(既不读工作目录,也无法用 run 的环境变量覆盖),因此任务直接下载到该目录。缺少则游戏无法切换语言(只有英文)且没有任何声音。

然后运行:

```bash
./gradlew runClient
./gradlew runServer
```

### 关键约束

**Java 7 是字节码硬上限。** 1.6.4 FML 内置 ASM 4.1 只能解析 class 版本 ≤51,`-source/-target` 可保持 1.6 或提到 1.7,但绝不可到 1.8——Java 8 字节码会让 FML 注解扫描抛 `IllegalArgumentException` 并丢弃整个 mod。这条针对编译产物,与跑 Gradle 用的 JDK 无关。

**资源输出目录。** `build.gradle` 用 `sourceSets.main.output.resourcesDir` 把资源指到 `build/classes/java/main`,因为 dev 环境 FML 把 `@Mod` 类所在目录当资源包根。若留在 Gradle 默认的 `build/resources/main`,游戏内贴图会紫黑、语言键不翻译。jar 打包不受影响。

**CME 补丁启动器(`src/launchPatch/`)。** Java 8u20+ 上原版 1.6.4 启动链必崩于 `ConcurrentModificationException`(launchwrapper 1.8 的 `Launch.launch()` 持迭代器跨回调,而 FML 的 `sortTweakList()` 会重排同一个 list;JDK-8030848 自 8u20 起让 `ArrayList.sort()` 无条件 `modCount++`)。`dev.launchfix.CmeSafeLaunch` 是 `Launch` 的等价实现,改成「每轮取列表头、处理完按引用移除」。独立 sourceSet 保证它不进发布 jar,只作用于 `runClient`/`runServer`。

发布须知:该补丁只覆盖 dev。让玩家在 8u20+ 的生产 Forge 上跑,终端用户仍需 [`legacyjavafixer-1.0.jar`](https://github.com/MinecraftForge/LegacyJavaFixer)。

更详细的成因分析与排错清单见模板仓库的 README。

---

powered by Gemini
