# 内存马注入靶场

基于 `memshell-party` SDK 的自包含靶场。每个工件里都嵌入了生成与加载逻辑，不依赖外部控制面，也不关心 RASP 或 BAS。

## 工件

| 工件 | 用途 | 启动方式 |
| --- | --- | --- |
| `range-war-javax.war` | javax Servlet 容器（Tomcat 9 / Jetty 9 等） | 放进中间件 `webapps` |
| `range-war-jakarta.war` | jakarta Servlet 容器（Tomcat 10.1 / 11 等） | 放进中间件 `webapps` |
| `range-springmvc-javax.jar` | Spring MVC，javax，内嵌 Tomcat | `java -jar` |
| `range-springmvc-jakarta.jar` | Spring MVC，jakarta，内嵌 Tomcat | `java -jar` |
| `range-webflux.jar` | Spring WebFlux，Netty | `java -jar` |

WAR 的上下文路径由文件名决定，例如部署为 `range.war` 时访问 `/range/`。Spring 工件默认监听 8080。

## 页面

部署后打开根路径即可。页面只使用原生 HTML/CSS/JS，不引用任何第三方资源，可离线使用。

- 覆盖矩阵：按服务、工具、挂载类型展示 SDK 实际支持的组合。
- 单个注入：选择工具与挂载类型，填写工具参数，注入后展示连接参数。
- 批量注入：对当前服务一次跑完矩阵，可选是否包含 Agent。
- 结果区：展示 `defined`、`instantiated`、`attached`、耗时、错误或警告，以及 URL、密码、密钥、请求头等连接信息。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/runtime` | 运行时信息，含服务、版本、Java、PID、Agent 能力 |
| GET | `/api/catalog?server=Tomcat` | 覆盖矩阵、工具参数、可用 packer |
| POST | `/api/inject` | 生成并注入一组 `{server,tool,mount,...}` |
| POST | `/api/generate` | 只生成，不加载 |
| POST | `/api/inject/batch` | 批量注入 |
| GET | `/api/injections` | 本次进程内的注入历史 |

`server` 省略时使用当前工件对应的服务。单个注入与批量注入都支持工具参数，例如 Godzilla 的 `pass`、`key`、`headerName`、`headerValue`。

批量注入示例：

```json
{"server": "Tomcat", "includeAgent": true}
```

也可以显式给出组合：

```json
{"cases": [
  {"server": "Tomcat", "tool": "Godzilla", "mount": "Filter"},
  {"server": "Tomcat", "tool": "Command", "mount": "Valve"}
]}
```

## Entrypoint

`/entrypoint` 接受 query、`application/x-www-form-urlencoded`、JSON 和 `text/plain` 四种参数形式，方便自动化直接调用。

- `mode=generate` 只生成，默认 `inject`。
- 不带 `payload` 时按 `server`、`tool`、`mount` 现场生成，不需要预先生成 Base64。
- 带 `payload`（或 `base64`、`data`）时直接加载已有 Base64。
- `method=agent` 或 `mount=Agent*` 时走 Agent 附加流程。

```bash
# 现场生成
curl 'http://127.0.0.1:8080/entrypoint?server=Tomcat&tool=Godzilla&mount=Filter&mode=generate'

# 加载已有 payload
curl -X POST http://127.0.0.1:8080/entrypoint \
  -H 'Content-Type: application/json' \
  -d '{"server":"Tomcat","method":"class","payload":"yv66vg..."}'
```

## 构建与测试

```bash
./gradlew assemble
./gradlew :integration-test:test
```

集成测试使用 Testcontainers，覆盖五个工件：Tomcat 9 javax、Tomcat 11 jakarta、Spring MVC javax、Spring MVC jakarta、Spring WebFlux。每个测试读取 `/api/catalog`，对矩阵里的每个工具与挂载组合调用 `/api/inject`，要求接口返回 200 且 `defined` 或 `attached` 为真。类成功定义即视为成功；实例化失败只作为 warning 记录，注入效果由 SDK 负责。

当前结果：

```text
Tomcat 9 javax        64 组合通过
Tomcat 11 jakarta     64 组合通过
Spring MVC javax      24 组合通过
Spring MVC jakarta    24 组合通过
Spring WebFlux        13 组合通过
```

Agent 挂载会启动同一 JDK 下的 `java -jar` 附加进程，因此运行靶场的 JVM 需要是 JDK，且 `java.home/bin/java` 可执行。容器镜像使用带 JDK 的版本。
