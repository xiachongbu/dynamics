# dynamics-example

Spring Boot 2.7 + H2 的集成示例，演示 SQL 动态替换与类热替换的完整调用与效果观察。

- 端口 **7071**
- 数据库用 H2 内存库，启动即建表灌数据，无需外部 MySQL
- **需要一个可用的 Redis**（集群同步的通道），默认连 `127.0.0.1:6379`

---

## 1. 启动

需要 JDK 8。

```bash
# 在仓库根目录先安装 agent / core / starter
mvn -pl dynamics-agent,dynamics-core,dynamics-spring-boot-starter -am install -DskipTests

# 再启动示例
mvn -pl dynamics-example spring-boot:run
```

Redis 有密码或不在默认地址时改 `src/main/resources/application.yml` 的 `spring.redis`，或用启动参数覆盖，避免把凭据写进仓库：

```bash
java -jar target/dynamics-example-1.0.0-SNAPSHOT.jar --spring.redis.password=你的密码
```

启动日志中应出现：

```
[dynamics] 热更新监听已启动, topic: dynamics-example:dynamics:update:topic
```

## 2. 配置授权名单

示例用请求头 `X-Dynamics-User` 充当登录态（**仅演示用，生产请接自己的登录上下文**）。名单未配置时所有热更新操作都会被拒绝。

```
redis-cli SET dynamics:authorized:users "alice"
```

验证拒绝路径：

```bash
curl -X POST http://127.0.0.1:7071/api/dynamics/mybatis/replace-sql \
  -H "X-Dynamics-User: mallory" \
  --data-urlencode "sqlStatementId=com.bjjw.dynamics.example.mapper.UserMapper.selectById" \
  --data-urlencode "sql=SELECT 1"
# {"code":500,"msg":"无权限执行此操作, 请联系管理员","data":null}
```

---

## 3. 演示一：替换 MyBatis SQL

替换前：

```bash
curl http://127.0.0.1:7071/demo/user/1
# {"id":1,"name":"alice","dept":"RD"}
```

下发替换（把 `dept` 换成常量）：

```bash
curl -X POST http://127.0.0.1:7071/api/dynamics/mybatis/replace-sql \
  -H "X-Dynamics-User: alice" \
  --data-urlencode "sqlStatementId=com.bjjw.dynamics.example.mapper.UserMapper.selectById" \
  --data-urlencode "sql=SELECT id, name, 'PATCHED' AS dept FROM t_user WHERE id = #{id}"
```

替换后：

```bash
curl http://127.0.0.1:7071/demo/user/1
# {"id":1,"name":"alice","dept":"PATCHED"}
```

### 3.1 验证 `<include>` 运行期展开

mapper 中定义了 `<sql id="userColumns">id, name, dept</sql>`。替换 SQL 里直接引用它：

```bash
curl -X POST http://127.0.0.1:7071/api/dynamics/mybatis/replace-sql \
  -H "X-Dynamics-User: alice" \
  --data-urlencode "sqlStatementId=com.bjjw.dynamics.example.mapper.UserMapper.selectById" \
  --data-urlencode "sql=SELECT <include refid=\"userColumns\"/> FROM t_user WHERE id = #{id} AND dept = 'OPS'"
```

此时 `curl http://127.0.0.1:7071/demo/user/1` 返回空（alice 的 dept 是 `RD`），而 `/demo/user/2` 返回 bob。若 `<include>` 未被展开，这条 SQL 会直接语法报错——能正常执行即证明展开生效。

---

## 4. 演示二：热替换类

替换前：

```bash
curl http://127.0.0.1:7071/demo/greeting
# v1
```

编译替换用的字节码（源码在 `src/main/resources/patch/GreetingService.java`，只改方法体、不动类结构）：

```bash
cd dynamics-example
javac -encoding UTF-8 -d target/patch src/main/resources/patch/GreetingService.java
```

上传：

```bash
curl -X POST http://127.0.0.1:7071/api/dynamics/class/update-by-upload \
  -H "X-Dynamics-User: alice" \
  -F "className=com.bjjw.dynamics.example.service.GreetingService" \
  -F "file=@target/patch/com/bjjw/dynamics/example/service/GreetingService.class"
```

替换后：

```bash
curl http://127.0.0.1:7071/demo/greeting
# v2-hot-patched
```

实例日志中应出现 agent 侧的完成记录：

```
INFO: dynamics => 类热替换完成: com.bjjw.dynamics.example.service.GreetingService, 已替换 1 个已加载副本
```

若看到 `目标类未被加载或不可修改` 或 `类热替换失败`，说明替换**没有生效**，按提示排查（常见原因：新字节码增删了方法/字段，或编译目标 JDK 高于运行进程）。

---

## 5. 注意

- 两类替换都只作用于**当前进程内存**，重启后回到原始版本。
- 接口返回成功仅代表指令已发布，实际结果看各实例日志；响应 `data.receivers` 是 Redis 回报的订阅者数量，小于实例数说明有实例没收到，为 0 时接口直接返回失败。
- 想验证集群同步，用不同端口起第二个实例（`--server.port=7072`），保持频道一致，向任一实例下发指令后两个实例都应生效。
- 示例的 `X-Dynamics-User` 请求头可被任意伪造，仅用于跑通流程，不可用于真实环境。