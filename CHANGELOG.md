# CHANGELOG

## 1.0.0-SNAPSHOT

首个开源版本。

### 能力

- **类热替换**：attach 当前 JVM，通过 `retransformClasses` 替换已加载类的方法体，无需重启。
- **MyBatis SQL 替换**：运行期重建 `MappedStatement` 的 `SqlSource`，支持动态标签，并单独补偿 `<include>` 展开。
- **集群同步**：REST 接口只下发指令，经 Redis 发布/订阅由各实例本地执行，含发起实例自身。
- **自动装配**：`dynamics-spring-boot-starter` 引入即生效，同时提供 Boot 2.x（`spring.factories`）与 Boot 3.x（`AutoConfiguration.imports`）两个入口。
- **可插拔权限**：`DynamicsAuthorizer` + `DynamicsUserProvider` 两个扩展点，默认实现为 Redis 用户名白名单，名单缺失时拒绝所有操作。

### 相对早期实现的修正

- **agent 改为源码构建**：`RedefineClass` / `ClassTransformer` 以真实源码参与编译，运行时从 classpath 读取字节码并程序化生成 MANIFEST 打成临时 agent jar；不再随仓库分发预编译二进制。
- **JNA 依赖显式化**：`byte-buddy-agent` 将 `jna` / `jna-platform` 声明为 `provided`，早期实现靠一个系统信息库的传递依赖间接满足，现直接声明，避免该库被移除后 attach 失败。
- **agent 参数切分**：`split("-", 2)` 替代 `split("-")`，临时目录路径含 `-` 时不再被截断导致替换静默失效。
- **失败不再静默**：
  - agent 侧先读取 class 文件再注册 transformer，读失败记 `SEVERE` 并终止；目标类未加载记 `WARNING`；`retransform` 失败记 `SEVERE`。
  - `updateClass` 失败抛 `IllegalStateException`；`detach` 移入 `finally`，`loadAgent` 失败不再泄漏 attach 连接。
  - SQL 替换遇到不支持的 `SqlSource` 类型、或反射写入失败时抛异常，不再吞掉异常报假成功。
  - 发布指令后校验 Redis `PUBLISH` 回报的订阅者数：为 0 时接口返回失败，并在响应 `data.receivers` 暴露实际送达数量。此前"发布成功但没有任何实例收到"（例如实例刚启动、订阅尚未建立）会静默丢失。
- **权限精确匹配**：授权名单按逗号分项精确比对，替代整串 `contains`（后者会让 `admin` 命中 `superadmin`）。
- **消息去重线程安全**：改用同步 LRU（`LinkedHashMap` + `removeEldestEntry`），替代非线程安全的 `HashSet` 与随机淘汰半数元素的清理方式。
- **去掉无谓等待**：`loadAgent` 为同步调用，移除其后的 `Thread.sleep(500)`（已实测替换仍生效）。
- **临时文件清理**：待替换 class 文件注册 `deleteOnExit`。
- **解耦**：`core` 不再依赖 Spring（资源读取改用 `ClassLoader`）；`starter` 用 Jackson 替代 fastjson，用 `ApplicationContext` 替代静态 Spring 上下文工具，统一响应体与权限判定自带实现，不依赖任何外部平台组件。
- **多数据源参数可选**：`sqlSessionFactory` 由必填改为可选，留空取 `dynamics.sql-session-factory` 配置值。

### 兼容性

- SDK 三模块（`agent` / `core` / `starter`）产出 **JDK 8 字节码**（major version 52），目标进程 JDK 8 及以上可用。
- attach 走 JNA 直连通道，**无需** `-Djdk.attach.allowAttachSelf=true`（JDK 8 / 17 实测）。
- `starter` 的 Spring 依赖为 `provided`，提供 Boot 2.x / 3.x 双装配入口，两条路线均已实测运行期装配与集群同步。
- **分支差异**：`main` 的 example 为 Boot 2.7 + JDK 8 + H2 1.4.200；本分支 `jdk17-plus` 为 Boot 3.3.5 + JDK 17 + H2 2.2.224，并需覆盖 parent 锁定的 slf4j 1.7（原因见 [docs/smoke-test.md](docs/smoke-test.md) §4.2）。SDK 与 parent 在两个分支上完全一致。

实测记录见 [docs/smoke-test.md](docs/smoke-test.md)。