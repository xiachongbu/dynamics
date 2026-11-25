# 冒烟测试记录（`jdk17-plus` 分支）

本文件记录本分支的实测结论与复现方式。**结论以实跑输出为准，不含静态推演。**

本分支基线：`example` 运行在 **Spring Boot 3.3.5 + JDK 17**；SDK 三模块（`agent` / `core` / `starter`）仍以 `source/target 8` 编译，与 `main` 分支一致。JDK 8 + Boot 2.7 侧的实测记录见 `main` 分支的同名文件。

## 1. 验证环境

| 项 | 值 |
|----|----|
| 操作系统 | Windows 10 (x64) |
| 编译 JDK | `17.0.15`（全部四个模块，SDK 三者以 `source/target 8` 产出） |
| 运行 JDK | `17.0.15` |
| 示例运行时 | Spring Boot 3.3.5 / Spring Framework 6.1.x / spring-data-redis 3.3.x / H2 2.2.224 / slf4j 2.0.16 |
| SDK 依赖 | byte-buddy-agent 1.12.13、jna/jna-platform 5.12.1、mybatis 3.5.3（core 编译）/ 3.5.13（示例运行）、commons-lang3 3.12.0、lombok 1.18.30 |
| Redis | 5.0.14.1（本机，密码认证） |

## 2. 编译与字节码分层校验

`mvn clean install -DskipTests`（JDK 17）BUILD SUCCESS。逐个读取 class 文件头第 7–8 字节机械校验 major version：

```
bytecode major versions (expect agent/core/starter=52, example=61):
  dynamics-agent                 {52: 2} (expect 52)
  dynamics-core                  {52: 4} (expect 52)
  dynamics-spring-boot-starter   {52: 14} (expect 52)
  dynamics-example               {61: 6} (expect 61)
total=26, mismatched=0
VERDICT=PASS
```

即：**用 JDK 17 构建，SDK 仍产出 JDK 8 字节码**，兼容面不因本分支而收窄。

## 3. Boot 3 双实例端到端实测

两个 example 实例（7071 / 7072）由 `mvn install` 产出的 Boot 3 fat jar 以 **JDK 17** 启动，共用频道 `dynamics-example:dynamics:update:topic`，连同一 Redis。

| 验证项 | 结果 |
|---|---|
| 两实例启动，**Boot 3 `AutoConfiguration.imports` 入口生效** | PASS |
| 非白名单用户下发指令被拒绝 | PASS |
| 经 A 下发 SQL 替换，`receivers=2` | PASS |
| SQL 替换在 **A 与 B 都生效**（`dept` → `PATCHED`） | PASS |
| 经 B 下发类替换，`receivers=2` | PASS |
| 类热替换在 **A 与 B 都生效**（`v1` → `v2-hot-patched`） | PASS |
| 含 `<include>` 的 SQL 替换在两实例生效 | PASS |
| 上传非 class 文件被拒绝 | PASS |

共 16 项断言，全部 PASS。

关键日志证据（两实例均出现）：

```
c.b.d.starter.DynamicsAutoConfiguration : [dynamics] 热更新监听已启动, topic: dynamics-example:dynamics:update:topic
[Attach Listener] com.bjjw.dynamics.agent.RedefineClass : dynamics => 类热替换完成: com.bjjw.dynamics.example.service.GreetingService, 已替换 1 个已加载副本
```

第二条来自 **agent 侧**，证明：JDK 8 字节码的 agent 能在 JDK 17 进程中完成 retransform；`core` 从 **Boot 3 fat jar 内**（`BOOT-INF/lib` 嵌套 jar）读取 agent 字节码资源同样正常。

attach **无需** `-Djdk.attach.allowAttachSelf=true`：走 byte-buddy 的 JNA 直连通道，不受 JDK 9+ self-attach 限制影响。

## 4. 跨大版本兼容的两个坑（均已实测踩到并修复）

### 4.1 `convertAndSend` 返回类型不一致

`RedisTemplate.convertAndSend(String, Object)` 在 **spring-data-redis 3.x 返回 `Long`，在 2.7.x 返回 `void`**。只核对 Boot 3 侧签名会得出"可以拿到订阅者数"的错误结论，按 Boot 2.7 编译时报 `incompatible types: void cannot be converted to java.lang.Long`。

改用两侧签名一致的底层命令接口：

```
2.7.11  RedisPubSubCommands.publish(byte[], byte[]) -> java.lang.Long
3.4.9   RedisPubSubCommands.publish(byte[], byte[]) -> java.lang.Long
```

**教训**：跨大版本兼容的 API 必须核对**编译侧**版本，而不只是目标运行侧。

### 4.2 parent 锁定的 slf4j 1.7 让 Boot 3 起不来

parent 的 `dependencyManagement` 为 JDK 8 主线锁了 `slf4j-api 1.7.36`。**继承自 parent 的 dependencyManagement 优先级高于子模块 import 的 BOM**，因此 Boot 3 BOM 里的 slf4j 2.0.x 被压回 1.7.36，启动直接失败：

```
java.lang.IllegalArgumentException: LoggerFactory is not a Logback LoggerContext but Logback is on the classpath.
Either remove Logback or the competing implementation
(class org.slf4j.helpers.NOPLoggerFactory loaded from ... slf4j-api-1.7.36.jar)
```

根因：slf4j 1.7 用 `StaticLoggerBinder` 绑定，2.x 改用 `ServiceLoader`；Boot 3 的 logback 1.5 只认后者，于是退化成 `NOPLoggerFactory`。

修复：在 `dynamics-example` 的 `dependencyManagement` 显式覆盖 `slf4j-api` 为 `2.0.16`（与已有的 mybatis 覆盖同一手法），SDK 与 parent 不动。

**排查提示**：把本 SDK 引入 Boot 3 应用时，若宿主工程的 dependencyManagement 也锁了 slf4j 1.7，会出现同样的启动失败——这与本 SDK 无关，需在宿主侧放开版本。

## 5. 通用结论（与 `main` 分支一致）

- **JNA 必须在运行时 classpath 上**：`byte-buddy-agent` 把 `jna` / `jna-platform` 声明为 `provided`，`dynamics-core` 已显式引入。缺失时 attach 抛 `NoClassDefFoundError: com/sun/jna/Platform`。
- **SQL 替换支持运行期 `<include>` 展开**：`<include>` 由 MyBatis 在 mapper 解析期展开，运行期拼 `<script>` 不经过该步骤，本工具手工补了一次。
- **agent 参数按首个 `-` 切分**（`split("-", 2)`），临时目录路径含 `-` 不会被截断。
- **发布指令校验订阅者数**：`receivers=0` 时接口返回失败；Redis pub/sub 不重放历史消息，实例订阅建立前收到的指令会被丢弃。

## 6. 尚未实测的部分

| 项 | 说明 |
|----|------|
| JDK 21 / Boot 4 组合 | 本分支验证到 JDK 17 + Boot 3.3；更高组合未测。SDK 侧无 JDK 17 专属 API，理论上可用，但未实证 |