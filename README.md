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

类名不在靶场里指定，由 SDK 按每次生成采样，返回值里的 `shellClassName` 和 `injectorClassName` 就是本次实际使用的名字。`obfuscate=true` 打开字节码混淆，默认关闭；同一个工具/挂载组合可以交替带与不带该参数，用于覆盖混淆与非混淆两条路径。

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
- `obfuscate=true` 时对生成物开启混淆，默认关闭。

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

附加进程是控制进程，不是被保护的应用，所以 `AgentAttacher` 会从子进程环境里去掉 `JAVA_TOOL_OPTIONS` / `JDK_JAVA_OPTIONS` / `_JAVA_OPTIONS` 中的 `-javaagent:` 项。探针只在靶场 JVM 里运行；如果附加进程也加载探针，它会以同一个容器注册成第二个应用，并让第二次及之后的 attach 握手失败。每次注入的结果会以 `[range] <caseId> <tool>/<mount> attached|defined|failed in <n>ms` 打到容器标准输出，BAS 证据里的 `docker.log` 可以直接看到附加进程返回的错误。

目标 JVM 连续被附加时，JDK attach 握手偶发失败，附加进程以 `IllegalStateException: Error during attachment` 退出，此时 agent 并未载入。`AgentAttacher` 对这种失败最多重试 3 次、间隔 500ms；超时和其他错误不重试。

## 容器镜像

`docker/` 下的 Dockerfile 把上面的工件封装成开箱即用的中间件镜像：

| 镜像 | 基础镜像 | 工件 | 上下文路径 |
| --- | --- | --- | --- |
| `memshell-range-tomcat-javax` | `cloudrasp/tomcat:9.0.84-jdk8` | `range-war-javax.war` | `/range` |
| `memshell-range-tomcat-jakarta` | `cloudrasp/tomcat:11.0.22-jdk17` | `range-war-jakarta.war` | `/range` |
| `memshell-range-jetty-javax` | `cloudrasp/jetty:9.4.19.v20190610` | `range-war-javax.war` | `/range` |
| `memshell-range-bes-javax` | `cloudrasp/bes:9.5.5` | `range-war-javax.war` | `/range` |
| `memshell-range-springmvc-javax` | `eclipse-temurin:11-jdk` | `range-springmvc-javax.jar` | `/` |
| `memshell-range-springmvc-jakarta` | `eclipse-temurin:17-jdk` | `range-springmvc-jakarta.jar` | `/` |
| `memshell-range-webflux` | `eclipse-temurin:17-jdk` | `range-webflux.jar` | `/` |

构建并推送 `linux/amd64,linux/arm64` 多架构镜像：

```bash
./docker/build.sh
```

只构建本地 amd64 镜像用于验证：

```bash
PLATFORMS=linux/amd64 PUSH=false ./docker/build.sh
```

`ONLY=tomcat-javax,webflux` 可只处理指定镜像。Dockerfile 不含 `RUN`，因此构建 arm64 不需要 binfmt/qemu。

## 镜像验证

`verify-range.sh` 启动镜像，读取 `/api/catalog`，对 Godzilla/Behinder/Suo5 的每个挂载组合调用 `/entrypoint`，要求返回 `defined` 或 `attached`。每个组合跑两遍，一遍不带混淆、一遍带 `obfuscate=true`；`MODES=plain` 或 `MODES=obfuscated` 可以只跑其中一遍：

```bash
./docker/verify-range.sh harbor.corp.boundaryx.net/cloudrasp/memshell-range-tomcat-javax:0.1.0 /range
# Spring 工件没有上下文路径，第二个参数显式传空串
SUITE=springmvc-javax ./docker/verify-range.sh harbor.corp.boundaryx.net/cloudrasp/memshell-range-springmvc-javax:0.1.0 ""
```

七个镜像的验证结果（2026-11-10，amd64，每行工具/挂载组合均含混淆与非混淆两遍）：

| 镜像 | Godzilla | Behinder | Suo5 | 合计 |
| --- | ---: | ---: | ---: | ---: |
| `memshell-range-tomcat-javax` | 10 | 7 | 7 | 48/48 |
| `memshell-range-tomcat-jakarta` | 10 | 7 | 7 | 48/48 |
| `memshell-range-jetty-javax` | 8 | 4 | 5 | 34/34 |
| `memshell-range-bes-javax` | 7 | 5 | 5 | 34/34 |
| `memshell-range-springmvc-javax` | 3 | 3 | 3 | 18/18 |
| `memshell-range-springmvc-jakarta` | 3 | 3 | 3 | 18/18 |
| `memshell-range-webflux` | 4 | 0 | 1 | 10/10 |

BES 9.5.5 把 `org.apache.catalina.*` 迁移到了 `com.bes.enterprise.webtier.*`。SDK 的 Valve 生成器会把模板里的 `org.apache.catalina.*` 重定位到 BES 的包名，但生成阶段仍需解析模板的父类 `org.apache.catalina.Valve`，所以两个 WAR 模块（`range-war-javax`、`range-war-jakarta`）在 `WEB-INF/lib` 里放入 `memshell-party-stub-tomcat`。Tomcat 的 Webapp 类加载器把 `org.apache.catalina.*` 委派给容器自带的 `catalina.jar`，stub 不会遮蔽真实类；BES 的类加载器则从 stub 解析父类，Valve 因此可以生成并挂到 `com.bes.enterprise.webtier` 的 Pipeline 上。Spring Boot 的 fat jar 使用扁平类加载器，stub 会排在 `tomcat-embed-core` 前面并导致 `NoSuchMethodError`，因此 stub 只加在 WAR 模块，不进 `range-core` 的共享依赖。`memshell-party-stub-jetty` 仍然排除，因为靶场自带真实 Jetty 类。

Jetty 的容器版本号（如 `9.4.19.v20190610`）不是 SDK Handler 模板认识的代际，`/entrypoint` 会归一化成 `7+` 后再生成。
