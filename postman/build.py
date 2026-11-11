#!/usr/bin/env python3
"""Build the Postman collections from catalog snapshots.

Each snapshot under postman/catalogs/ is the exact /api/catalog response of one
image. The generator turns every advertised tool/mount pair into one request,
so a collection cannot drift from what the running image supports. Refresh the
snapshots with postman/collect-catalogs.sh after changing the SDK.

    python3 -I postman/build.py
"""
import json
import pathlib
import urllib.parse
import uuid

ROOT = pathlib.Path(__file__).resolve().parent
CATALOGS = ROOT / "catalogs"

SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"

TARGETS = [
    {
        "key": "tomcat-javax",
        "folder": "Tomcat 9 · javax",
        "image": "memshell-range-tomcat-javax",
        "variable": "tomcatJavaxBaseUrl",
        "url": "http://127.0.0.1:8080/range",
    },
    {
        "key": "tomcat-jakarta",
        "folder": "Tomcat 11 · jakarta",
        "image": "memshell-range-tomcat-jakarta",
        "variable": "tomcatJakartaBaseUrl",
        "url": "http://127.0.0.1:8080/range",
    },
    {
        "key": "jetty-javax",
        "folder": "Jetty 9 · javax",
        "image": "memshell-range-jetty-javax",
        "variable": "jettyJavaxBaseUrl",
        "url": "http://127.0.0.1:8080/range",
    },
    {
        "key": "springmvc-javax",
        "folder": "Spring MVC · Boot 2.7 · javax",
        "image": "memshell-range-springmvc-javax",
        "variable": "springMvcJavaxBaseUrl",
        "url": "http://127.0.0.1:8080",
    },
    {
        "key": "springmvc-jakarta",
        "folder": "Spring MVC · Boot 3.3 · jakarta",
        "image": "memshell-range-springmvc-jakarta",
        "variable": "springMvcJakartaBaseUrl",
        "url": "http://127.0.0.1:8080",
    },
    {
        "key": "webflux",
        "folder": "Spring WebFlux · Boot 3.3",
        "image": "memshell-range-webflux",
        "variable": "webfluxBaseUrl",
        "url": "http://127.0.0.1:8080",
    },
]

INJECT_INTRO = """按工具与挂载类型逐个调用 `/entrypoint`，服务端生成并加载内存马。

使用步骤：
1. 启动一个镜像，例如 `docker run --rm -p 8080:8080 ghcr.io/OWNER/REPO/memshell-range-tomcat-javax:0.1.0`。
2. 在环境 `memshell-range` 里把对应变量改成本机地址。Tomcat 和 Jetty 带 `/range` 上下文，Spring 镜像不带。
3. 运行一个镜像目录下的请求。每个请求带 `server`、`tool`、`mount` 三个参数。

集合级测试断言 HTTP 200 且响应中的 `defined` 或 `attached` 为真。Agent 类挂载会启动附加进程，耗时明显长于类加载挂载。
"""

GENERATE_INTRO = """按工具与挂载类型逐个调用 `/entrypoint?mode=generate`，只生成并返回 Base64，不加载。

使用步骤与注入集合相同。集合级测试断言 HTTP 200 且 `payloadBase64` 非空。响应里的 `shellClassName` 和 `injectorClassName` 是本次采样到的类名，每次都可能不同。
"""

INJECT_TEST = [
    'const body = pm.response.json();',
    'pm.test("HTTP 200", function () { pm.response.to.have.status(200); });',
    'pm.test("defined or attached", function () {',
    '    pm.expect(body.defined === true || body.attached === true,',
    '        "error=" + (body.error || "") + " warning=" + (body.warning || "")).to.be.true;',
    '});',
]

GENERATE_TEST = [
    'const body = pm.response.json();',
    'pm.test("HTTP 200", function () { pm.response.to.have.status(200); });',
    'pm.test("payload generated", function () {',
    '    pm.expect(typeof body.payloadBase64 === "string" && body.payloadBase64.length > 0,',
    '        "error=" + (body.error || "")).to.be.true;',
    '});',
]


def load_catalogs():
    missing = [t["key"] for t in TARGETS if not (CATALOGS / (t["key"] + ".json")).is_file()]
    if missing:
        raise SystemExit("missing catalog snapshots: " + ", ".join(missing)
                         + "\nrun postman/collect-catalogs.sh first")
    out = {}
    for target in TARGETS:
        with (CATALOGS / (target["key"] + ".json")).open(encoding="utf-8") as handle:
            out[target["key"]] = json.load(handle)
    return out


def request_item(target, server, tool, mount, mode):
    query = [
        ("server", server),
        ("tool", tool),
        ("mount", mount),
    ]
    if mode == "generate":
        query.append(("mode", "generate"))
    raw = "{{" + target["variable"] + "}}/entrypoint?" + urllib.parse.urlencode(query)
    name = tool + " / " + mount
    if mode == "generate":
        description = "只生成 " + tool + " 的 " + mount + " 类型内存马，不加载。"
    else:
        description = "生成并注入 " + tool + " 的 " + mount + " 类型内存马。"
    return {
        "name": name,
        "request": {
            "method": "GET",
            "header": [],
            "url": {
                "raw": raw,
                "host": ["{{" + target["variable"] + "}}"],
                "path": ["entrypoint"],
                "query": [{"key": key, "value": value} for key, value in query],
            },
            "description": description,
        },
        "response": [],
    }


def build_collection(mode, catalogs):
    items = []
    total = 0
    for target in TARGETS:
        catalog = catalogs[target["key"]]
        server = catalog["server"]
        tools = []
        for tool, mounts in catalog["matrix"].items():
            requests = [request_item(target, server, tool, mount, mode) for mount in mounts]
            total += len(requests)
            tools.append({
                "name": tool + " (" + str(len(requests)) + ")",
                "description": tool + " 在 " + target["folder"] + " 上支持的挂载类型。",
                "item": requests,
            })
        items.append({
            "name": target["folder"] + " · " + str(sum(len(t["item"]) for t in tools)) + " 种",
            "description": "镜像 `" + target["image"] + "`，服务端标识 `" + server + "`。",
            "item": tools,
        })

    is_inject = mode == "inject"
    collection = {
        "info": {
            "name": "memshell-range · " + ("注入 (inject)" if is_inject else "生成 (generate)"),
            "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "memshell-range/" + mode)),
            "description": INJECT_INTRO if is_inject else GENERATE_INTRO,
            "schema": SCHEMA,
        },
        "event": [{
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": INJECT_TEST if is_inject else GENERATE_TEST,
            },
        }],
        "variable": [
            {"key": t["variable"], "value": t["url"], "type": "string"}
            for t in TARGETS
        ],
        "item": items,
    }
    return collection, total


def build_environment():
    return {
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "memshell-range/environment")),
        "name": "memshell-range",
        "values": [
            {"key": t["variable"], "value": t["url"], "type": "default", "enabled": True}
            for t in TARGETS
        ],
        "_postman_variable_scope": "environment",
    }


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    catalogs = load_catalogs()
    for mode, filename in (("inject", "memshell-range-inject.postman_collection.json"),
                           ("generate", "memshell-range-generate.postman_collection.json")):
        collection, total = build_collection(mode, catalogs)
        write_json(ROOT / filename, collection)
        print(filename + ": " + str(total) + " requests")
    write_json(ROOT / "memshell-range.postman_environment.json", build_environment())
    print("memshell-range.postman_environment.json: " + str(len(TARGETS)) + " variables")


if __name__ == "__main__":
    main()
