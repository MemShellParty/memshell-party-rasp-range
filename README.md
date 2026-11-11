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

`docker/` 下的 Dockerfile 把上面的工件封装成开箱即用的中间件镜像。六个镜像使用 Docker Hub 上的公开基础镜像，由 GitHub Actions 构建并推送到 GitHub Packages（GHCR）：

| 镜像 | 基础镜像 | 工件 | 上下文路径 |
| --- | --- | --- | --- |
| `memshell-range-tomcat-javax` | `tomcat:9.0.123-jdk8-temurin-jammy` | `range-war-javax.war` | `/range` |
| `memshell-range-tomcat-jakarta` | `tomcat:11.0.27-jdk17-temurin-jammy` | `range-war-jakarta.war` | `/range` |
| `memshell-range-jetty-javax` | `jetty:9.4.58-jdk8-eclipse-temurin` | `range-war-javax.war` | `/range` |
| `memshell-range-springmvc-javax` | `eclipse-temurin:11-jdk` | `range-springmvc-javax.jar` | `/` |
| `memshell-range-springmvc-jakarta` | `eclipse-temurin:17-jdk` | `range-springmvc-jakarta.jar` | `/` |
| `memshell-range-webflux` | `eclipse-temurin:17-jdk` | `range-webflux.jar` | `/` |

GHCR 地址为 `ghcr.io/MemShellParty/memshell-party-rasp-range/memshell-range-<name>:<tag>`，`<tag>` 是 `0.1.0`、`latest` 或提交短 SHA。

BES 9.5.5 依赖内部 Harbor 基础镜像，不在默认的六个之列。需要时单独构建：

```bash
INCLUDE_BES=1 ./docker/build.sh ONLY=bes-javax
```

构建并推送 `linux/amd64,linux/arm64` 两个架构：

```bash
./docker/build.sh
```

只构建本地 amd64 镜像用于验证：

```bash
PUSH=false PLATFORMS=linux/amd64 ./docker/build.sh
```

`REGISTRY`、`TAG`、`PLATFORMS`、`ONLY` 都可以覆盖。Dockerfile 不含 `RUN`，构建 arm64 不需要 binfmt/qemu。

## CI

`.github/workflows/images.yml` 在 push 到 `main`、推送 `v*` 标签、Pull Request 和手动触发时运行：

- `artifacts`：用 JDK 17 构建五个工件。
- `verify`：用 Testcontainers 跑集成测试。
- `postman`：重新生成 Postman 集合，并检查提交的 JSON 与 catalog 快照一致。
- `images`：按矩阵构建六个镜像。`main` 和标签推送时推送到 GHCR 的 `linux/amd64,linux/arm64`，PR 只构建 `linux/amd64` 不推送。

仓库是私有的，所以 GHCR 包默认也是私有的。同一个仓库的 workflow 用 `GITHUB_TOKEN` 拉取：

```yaml
permissions:
  packages: read
steps:
  - run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
  - run: docker pull ghcr.io/MemShellParty/memshell-party-rasp-range/memshell-range-tomcat-javax:0.1.0
```

其他仓库或本地拉取时，需要在包的设置里把仓库加入 Actions 访问，或者使用带 `read:packages` 的 PAT。要把镜像公开给匿名 CI，在包的设置里把 visibility 改成 public。

## Postman

`postman/` 下有两个可直接导入 Postman 的集合和一个环境：

| 文件 | 用途 | 请求数 |
| --- | --- | ---: |
| `memshell-range-inject.postman_collection.json` | 调用 `/entrypoint` 生成并注入 | 233 |
| `memshell-range-generate.postman_collection.json` | 调用 `/entrypoint?mode=generate` 只生成 | 233 |
| `memshell-range.postman_environment.json` | 六个镜像的基础地址 | 6 |

集合按镜像分目录，目录内按工具分组，每个工具和挂载类型一个请求。请求参数是 `server`、`tool`、`mount`，生成集合额外带 `mode=generate`。集合级测试脚本断言注入返回 `defined` 或 `attached`，生成返回非空 `payloadBase64`。

使用：

1. 启动一个镜像，例如 `docker run --rm -p 8080:8080 ghcr.io/MemShellParty/memshell-party-rasp-range/memshell-range-tomcat-javax:0.1.0`。
2. 导入两个集合和环境，选择环境 `memshell-range`。
3. 修改对应镜像的地址变量。Tomcat 和 Jetty 用 `http://127.0.0.1:8080/range`，Spring 镜像用 `http://127.0.0.1:8080`。
4. 打开该镜像目录，运行需要的工具分组。Agent 挂载会启动附加进程，耗时更长。

集合由 `postman/build.py` 从真实 catalog 快照生成，不要手改。刷新快照并重新生成：

```bash
./postman/collect-catalogs.sh
python3 -I postman/build.py
```

## 镜像验证

`verify-range.sh` 启动镜像，读取 `/api/catalog`，对 Godzilla/Behinder/Suo5 的每个挂载组合调用 `/entrypoint`，要求返回 `defined` 或 `attached`。每个组合跑两遍，一遍不带混淆、一遍带 `obfuscate=true`；`MODES=plain` 或 `MODES=obfuscated` 可以只跑其中一遍：

```bash
./docker/verify-range.sh range-local/memshell-range-tomcat-javax:0.1.0 /range
SUITE=springmvc-javax ./docker/verify-range.sh range-local/memshell-range-springmvc-javax:0.1.0 ""
```

六个镜像的验证结果（2026-11-11，amd64，每行工具/挂载组合均含混淆与非混淆两遍）：

| 镜像 | Godzilla | Behinder | Suo5 | 合计 |
| --- | ---: | ---: | ---: | ---: |
| `memshell-range-tomcat-javax` | 10 | 7 | 7 | 48/48 |
| `memshell-range-tomcat-jakarta` | 10 | 7 | 7 | 48/48 |
| `memshell-range-jetty-javax` | 8 | 4 | 5 | 34/34 |
| `memshell-range-springmvc-javax` | 3 | 3 | 3 | 18/18 |
| `memshell-range-springmvc-jakarta` | 3 | 3 | 3 | 18/18 |
| `memshell-range-webflux` | 4 | 0 | 1 | 10/10 |

BES 9.5.5 把 `org.apache.catalina.*` 迁移到了 `com.bes.enterprise.webtier.*`。SDK 的 Valve 生成器会把模板里的 `org.apache.catalina.*` 重定位到 BES 的包名，但生成阶段仍需解析模板的父类 `org.apache.catalina.Valve`，所以两个 WAR 模块（`range-war-javax`、`range-war-jakarta`）在 `WEB-INF/lib` 里放入 `memshell-party-stub-tomcat`。Tomcat 的 Webapp 类加载器把 `org.apache.catalina.*` 委派给容器自带的 `catalina.jar`，stub 不会遮蔽真实类；BES 的类加载器则从 stub 解析父类，Valve 因此可以生成并挂到 `com.bes.enterprise.webtier` 的 Pipeline 上。Spring Boot 的 fat jar 使用扁平类加载器，stub 会排在 `tomcat-embed-core` 前面并导致 `NoSuchMethodError`，因此 stub 只加在 WAR 模块，不进 `range-core` 的共享依赖。`memshell-party-stub-jetty` 仍然排除，因为靶场自带真实 Jetty 类。

Jetty 官方镜像不再提供 `9.4.19` 标签，公开基础镜像改用最后一个 9.4 版本 `9.4.58`。SDK 的 Handler 模板按代际选择，`/entrypoint` 会把容器版本（如 `9.4.58.v20250814`）归一化成 `7+` 后再生成，验证结果与 9.4.19 一致。
