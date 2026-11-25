# 冒烟测试记录

本文件记录首个开源版本的实测结论与复现方式。**结论以实跑输出为准，不含静态推演。**

主线基线为 **JDK 8**：四个模块全部以 `source/target 8` 编译，示例运行于 Spring Boot 2.7。面向 JDK 17+ / Boot 3 的示例与运行期验证将在独立分支提供。

## 1. 验证环境

| 项 | 值 |
|----|----|
| 操作系统 | Windows 10 (x64) |
| 编译 JDK | `1.8.0_121`（全部四个模块） |
| 运行 JDK | `1.8.0_121`、`17.0.15` |
| 依赖 | byte-buddy-agent 1.12.13、jna/jna-platform 5.12.1、mybatis 3.5.3（core）/ 3.5.13（example）、slf4j-api 1.7.36、commons-lang3 3.12.0、lombok 1.18.30 |
| 示例编译期 Spring | Spring Boot 2.7.18 / Spring Framework 5.3.33 / javax.servlet-api 4.0.1 |

验证走 `javac` 直接编译 + 独立 JVM 进程实跑，不依赖 Maven 构建，便于在离线环境复核。

## 2. 编译与字节码校验

| 模块 | 源文件数 | 编译 JDK | 结果 |
|------|---------|---------|------|
| `dynamics-agent` | 2 | 8 | 通过 |
| `dynamics-core` | 4 | 8 | 通过 |
| `dynamics-spring-boot-starter` | 9 | 8 | 通过 |
| `dynamics-example` | 6 | 8 | 通过 |

逐个读取 class 文件头第 7–8 字节机械校验 major version（不抽样、不肉眼判断）：

```
bytecode major version check (expect 52 for every class):
  agent    {52: 2}
  core     {52: 4}
  starter  {52: 14}
  example  {52: 6}
total classes=26, non-52=0
VERDICT=PASS
```

## 3. 类热替换实跑

验证程序先实例化目标类并打印 `greeting()`，再调用 `ClassService.getInstance().updateClass(...)`，然后重新调用同一实例的方法。目标类新旧两版仅方法体不同。

### 3.1 JDK 8，无任何附加 JVM 参数

```
BEFORE=v1
AFTER=v2-hot-patched
VERDICT=PASS
INFO: dynamics => 类热替换完成: verify.TargetService, 已替换 1 个已加载副本
```

### 3.2 JDK 8，class 文件路径含 `-`

agent 参数形如 `className-classPath`，早期实现用 `split("-")` 取第 2 段，路径含 `-` 时会被截断。现按 `split("-", 2)` 切分。用含 `-` 的目录（`patch-dir-with-dash`）作为 class 文件路径：

```
BEFORE=v1
AFTER=v2-hot-patched
VERDICT=PASS
```

### 3.3 JDK 17 运行 JDK 8 字节码产物，**不加** `-Djdk.attach.allowAttachSelf=true`

```
BEFORE=v1
AFTER=v2-hot-patched
VERDICT=PASS
```

**结论**：attach 走 byte-buddy 的 JNA 直连通道，JDK 9+ 的 self-attach 限制不适用，无需附加参数；JDK 8 字节码的 agent 可直接在 JDK 17 进程中完成替换。同时验证了 `loadAgent` 是同步调用——去掉早期实现中其后的 `Thread.sleep(500)` 后替换依然生效。

### 3.4 缺少 JNA 时的表现

移除 classpath 中的 jna 后：

```
Exception in thread "main" java.lang.NoClassDefFoundError: com/sun/jna/Platform
	at net.bytebuddy.agent.VirtualMachine$ForHotSpot.attach(VirtualMachine.java:240)
```

`byte-buddy-agent` 的 pom 把 `jna` / `jna-platform` 声明为 `provided`，因此 `dynamics-core` 显式引入二者。排查 attach 失败时先确认这两个 jar 在运行时 classpath 上。

## 4. MyBatis SQL 替换实跑

用 `XMLMapperBuilder` 解析含 `<sql id="userColumns">` 与 `<include>` 的 mapper，替换后再取 `BoundSql`：

```
SQL_BEFORE=SELECT id, name, dept FROM t_user WHERE id = ?
SQL_AFTER=SELECT id, name, dept , 'patched' AS mark FROM t_user WHERE id = ?
VERDICT=PASS
```

判定条件：结果 SQL 含展开后的 `id, name, dept`、不含 `include` 残留、且含新增的 `'patched' AS mark`。

**结论**：运行期拼 `<script>` 时 `<include>` 被正确展开，`#{id}` 仍正常解析为占位符。

## 5. Boot 3 二进制兼容静态核对

`starter` 提供 Boot 3 装配入口，且 Spring 依赖均为 `provided`。为确认其调用的 API 在 Boot 3 侧签名未变（供后续 17+ 分支使用），对 Boot 3 侧 jar 执行 `javap`：

| 类（Boot 3 侧版本） | 签名 | 结论 |
|---|---|---|
| `MessageListenerAdapter`（spring-data-redis 3.4.9） | `MessageListenerAdapter(Object, String)` | 存在 |
| `RedisMessageListenerContainer`（同上） | `addMessageListener(MessageListener, Topic)` | 存在 |
| `RedisTemplate`（同上） | `Long convertAndSend(String, Object)` | 存在 |
| `MessageListener`（同上） | `void onMessage(Message, byte[])` | 存在 |
| `MultipartFile`（spring-web 6.1.8） | `byte[] getBytes()` | 存在 |
| `AutoConfiguration`（spring-boot-autoconfigure 3.3.0） | 注解类型存在 | 存在 |

`starter` 不使用 `javax.servlet` / `jakarta.servlet`，因此不受 Servlet 命名空间迁移影响。**这是静态签名核对，不等于 Boot 3 运行期装配已验证。**

### 5.1 一个被静态核对漏掉的差异

`RedisTemplate.convertAndSend(String, Object)` 在 **3.4.9 返回 `Long`，在 2.7.11 返回 `void`**。只核对 Boot 3 侧签名会得出"可以拿到订阅者数"的错误结论，按 Boot 2.7 编译时直接报 `incompatible types: void cannot be converted to java.lang.Long`。

最终改用两侧签名一致的底层命令接口：

```
2.7.11  RedisPubSubCommands.publish(byte[], byte[]) -> java.lang.Long
3.4.9   RedisPubSubCommands.publish(byte[], byte[]) -> java.lang.Long
```

**教训**：跨大版本兼容的 API 必须核对**编译侧**版本，而不只是目标运行侧。

## 6. 端到端实测（双实例 + 真实 Redis）

环境：Redis 5.0.14.1（本机，密码认证），两个 example 实例分别监听 7071 / 7072，共用频道 `dynamics-example:dynamics:update:topic`；均由 `mvn install` 产出的 fat jar 以 JDK 8 启动。

| 验证项 | 结果 |
|---|---|
| 两实例启动，`starter` 自动装配并订阅频道 | PASS（日志出现 `[dynamics] 热更新监听已启动, topic: ...`） |
| 非白名单用户下发指令被拒绝 | PASS（`code=500`，"无权限执行此操作"） |
| 无用户名（未接入登录态）被拒绝 | PASS（`code=500`，"未获取到当前用户"） |
| 经实例 A 下发 SQL 替换，`receivers=2` | PASS |
| SQL 替换在 **A 与 B 都生效**（`dept` → `PATCHED`） | PASS |
| 经实例 B 下发类替换，`receivers=2` | PASS |
| 类热替换在 **A 与 B 都生效**（`v1` → `v2-hot-patched`） | PASS |
| 含 `<include>` 的 SQL 替换在两实例生效 | PASS（`user1` 被过滤为空、`user2` 仍可见） |
| 上传非 class 文件被拒绝 | PASS（"上传的文件不是有效的 class 文件"） |

### 6.1 订阅者计数与频道隔离

另起两个实例 C（7073，频道 `isolated-app:dynamics:update:topic`）与 D（7074，默认频道）：

| 验证项 | 结果 |
|---|---|
| Redis 对无订阅者频道 `PUBLISH` 返回 `:0` | PASS |
| 经 C 下发，`receivers=1`（仅 C 订阅该频道） | PASS |
| C 生效（`dept` → `FROM-C`），**D 不受影响**（仍 `RD`） | PASS |
| 经 D 下发，`receivers=1`，D 生效（`FROM-D`），**C 保持 `FROM-C`** | PASS |

证明 `receivers` 反映真实订阅者数量，且频道按应用隔离有效。

### 6.2 实测暴露的启动竞态

首轮测试中，第一次 SQL 替换**接口返回成功但两实例都没生效**。日志显示只有 Controller 的"指令已发布"，没有任何实例的"收到热更新指令"：发布时刻比应用 `Started` 早 30ms，Redis 订阅尚未建立，pub/sub 直接丢弃了消息。

据此改为校验 `PUBLISH` 回报的订阅者数（见 §5.1），`receivers=0` 时接口返回失败。修复后重跑，全部 18 项断言 PASS。

**这是 Redis pub/sub 的固有语义**（不重放历史消息），因此文档明确要求用 `receivers` 核对送达数量，而不是把"接口 200"当作生效凭据。

## 7. 尚未实测的部分

| 项 | 原因 | 复核方式 |
|----|------|---------|
| Boot 3 应用中的运行期装配 | 主线为 JDK 8 / Boot 2.7，示例跑在 Boot 2.7 | 在独立分支的 JDK 17+ 示例中验证（§5 的静态核对已覆盖除 `convertAndSend` 外的调用点） |