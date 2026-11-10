package com.range.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RangeApi {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RuntimeInfo runtime;
    private final InjectService injectService = new InjectService();
    private final CatalogService catalogService = new CatalogService();

    public RangeApi(RuntimeInfo runtime) {
        this.runtime = runtime;
    }

    public Response handle(String method, String path, Map<String, String> query, byte[] body,
                           String contentType, String baseUrl) {
        String route = route(path);
        String httpMethod = method == null ? "GET" : method.toUpperCase();
        String base = trimSlash(baseUrl);
        try {
            if ("/runtime".equals(route) || "/".equals(route)) {
                return Response.json(200, runtime);
            }
            if ("/catalog".equals(route)) {
                return Response.json(200, catalogService.catalog(runtime, param(query, null, "server")));
            }
            if ("/inject".equals(route)) {
                if (!"POST".equals(httpMethod)) {
                    return Response.json(405, error("use POST"));
                }
                Map<String, String> params = flatten(readBody(body, contentType), query);
                return inject(params, base);
            }
            if ("/generate".equals(route)) {
                if (!"POST".equals(httpMethod)) {
                    return Response.json(405, error("use POST"));
                }
                Map<String, String> params = flatten(readBody(body, contentType), query);
                ShellCase shellCase = CaseBuilder.build(runtime, params);
                if (shellCase.getTool().isEmpty() || shellCase.getMount().isEmpty()) {
                    return Response.json(400, error("server, tool and mount are required"));
                }
                return Response.json(200, injectService.generate(shellCase, base));
            }
            if ("/inject/batch".equals(route)) {
                if (!"POST".equals(httpMethod)) {
                    return Response.json(405, error("use POST"));
                }
                return batch(readBody(body, contentType), query, base);
            }
            if ("/injections".equals(route)) {
                return Response.json(200, injectService.history());
            }
            if ("/injections/clear".equals(route)) {
                injectService.clearHistory();
                return Response.json(200, error("cleared"));
            }
            if ("/entrypoint".equals(route)) {
                return entrypoint(readBody(body, contentType), query, body, contentType, base);
            }
            return Response.json(404, error("unknown path: " + path));
        } catch (IllegalArgumentException e) {
            return Response.json(400, error(e.getMessage()));
        } catch (Throwable t) {
            return Response.json(500, error(t.getClass().getName() + ": " + t.getMessage()));
        }
    }

    private Response inject(Map<String, String> params, String base) {
        ShellCase shellCase = CaseBuilder.build(runtime, params);
        if (shellCase.getTool().isEmpty() || shellCase.getMount().isEmpty()) {
            return Response.json(400, error("server, tool and mount are required"));
        }
        return Response.json(200, injectService.inject(shellCase, base));
    }

    private Response batch(Map<String, Object> body, Map<String, String> query, String base) {
        List<Map<String, String>> inputs = new ArrayList<Map<String, String>>();
        Object rawCases = body.get("cases");
        if (rawCases instanceof List) {
            for (Object item : (List<?>) rawCases) {
                if (item instanceof Map) {
                    inputs.add(flatten(asMap(item), query));
                }
            }
        } else {
            Map<String, String> selector = flatten(body, query);
            String server = selector.get("server");
            Map<String, Object> catalog = catalogService.catalog(runtime, server);
            @SuppressWarnings("unchecked")
            Map<String, List<String>> matrix = (Map<String, List<String>>) catalog.get("matrix");
            boolean includeAgent = !"false".equalsIgnoreCase(selector.get("includeAgent"));
            List<String> tools = list(body.get("tools"));
            if (tools.isEmpty()) {
                tools = new ArrayList<String>(matrix.keySet());
            }
            List<String> requestedMounts = list(body.get("mounts"));
            for (String tool : tools) {
                List<String> mounts = matrix.get(tool);
                if (mounts == null) {
                    continue;
                }
                for (String mount : mounts) {
                    if (!includeAgent && mount.startsWith("Agent")) {
                        continue;
                    }
                    if (!requestedMounts.isEmpty() && !requestedMounts.contains(mount)) {
                        continue;
                    }
                    Map<String, String> input = new LinkedHashMap<String, String>(selector);
                    input.put("server", (String) catalog.get("server"));
                    input.put("tool", tool);
                    input.put("mount", mount);
                    inputs.add(input);
                }
            }
        }
        if (inputs.size() > 500) {
            throw new IllegalArgumentException("too many cases: " + inputs.size() + ", limit 500");
        }
        List<InjectResult> results = new ArrayList<InjectResult>();
        int ok = 0;
        for (Map<String, String> input : inputs) {
            InjectResult result = injectService.inject(CaseBuilder.build(runtime, input), base);
            if (result.isDefined() || result.isAttached()) {
                ok++;
            }
            results.add(result);
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("total", results.size());
        response.put("succeeded", ok);
        response.put("failed", results.size() - ok);
        response.put("results", results);
        return Response.json(200, response);
    }

    private Response entrypoint(Map<String, Object> body, Map<String, String> query, byte[] rawBody,
                                String contentType, String base) {
        Map<String, String> params = flatten(body, query);
        if (isPlain(contentType) && rawBody != null && rawBody.length > 0 && !params.containsKey("payload")) {
            params.put("payload", new String(rawBody, StandardCharsets.UTF_8).trim());
        }
        String mode = first(params.get("mode"), first(params.get("action"), "inject"));
        String payload = first(params.get("payload"), first(params.get("base64"), params.get("data")));
        if (payload != null && !payload.isEmpty()) {
            if (isAgent(params)) {
                params.put("mount", "Agent");
            }
            ShellCase shellCase = CaseBuilder.build(runtime, params);
            if ("generate".equalsIgnoreCase(mode)) {
                return Response.json(200, new GenerateResult(shellCase.getCaseId(), shellCase.getServer(),
                        shellCase.getMount(), shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(),
                        payload, payload.length(), null, ConnectionInfo.from(shellCase, base)));
            }
            return Response.json(200, injectService.injectPayload(shellCase, payload, base));
        }
        if (!params.containsKey("tool")) {
            params.put("tool", param(query, body, "shellTool"));
        }
        if (!params.containsKey("mount")) {
            params.put("mount", param(query, body, "type"));
        }
        if (!params.containsKey("mount")) {
            params.put("mount", param(query, body, "shellType"));
        }
        ShellCase shellCase = CaseBuilder.build(runtime, params);
        if (shellCase.getTool().isEmpty() || shellCase.getMount().isEmpty()) {
            return Response.json(400, error("tool/mount or payload is required"));
        }
        if ("generate".equalsIgnoreCase(mode)) {
            return Response.json(200, injectService.generate(shellCase, base));
        }
        return Response.json(200, injectService.inject(shellCase, base));
    }

    private static boolean isAgent(Map<String, String> params) {
        String method = params.get("method");
        if (method != null && "agent".equalsIgnoreCase(method.trim())) {
            return true;
        }
        String mount = params.get("mount");
        if (mount != null && mount.startsWith("Agent")) {
            return true;
        }
        String packer = params.get("packer");
        return packer != null && packer.startsWith("AgentJar");
    }

    private Map<String, Object> readBody(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return new LinkedHashMap<String, Object>();
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            if (isJson(contentType) || text.startsWith("{")) {
                return asMap(mapper.readValue(text, Map.class));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid JSON body: " + e.getMessage());
        }
        if (isForm(contentType) || text.contains("=")) {
            return asMap(parseForm(text));
        }
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("payload", text);
        return raw;
    }

    public static Map<String, String> query(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static Map<String, Object> parseForm(String text) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (String pair : text.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private static Map<String, String> flatten(Map<String, Object> body, Map<String, String> query) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        if (query != null) {
            params.putAll(query);
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List) {
                continue;
            }
            if (value != null) {
                params.put(entry.getKey(), String.valueOf(value));
            }
        }
        return params;
    }

    private static List<String> list(Object value) {
        List<String> values = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (value != null) {
            for (String item : String.valueOf(value).split(",")) {
                if (!item.trim().isEmpty()) {
                    values.add(item.trim());
                }
            }
        }
        return values;
    }

    private static String param(Map<String, String> query, Map<String, Object> body, String name) {
        String value = body == null ? null : (body.get(name) == null ? null : String.valueOf(body.get(name)));
        return value != null ? value : (query == null ? null : query.get(name));
    }

    private static String route(String path) {
        String value = path == null ? "" : path;
        int entry = value.lastIndexOf("/entrypoint");
        if (entry >= 0) {
            return "/entrypoint";
        }
        int api = value.indexOf("/api");
        if (api >= 0) {
            value = value.substring(api + 4);
        }
        if (value.isEmpty()) {
            return "/";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("json");
    }

    private static boolean isForm(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("x-www-form-urlencoded");
    }

    private static boolean isPlain(String contentType) {
        return contentType == null || contentType.toLowerCase().contains("text/plain");
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String first(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("error", message);
        return error;
    }

    public static final class Response {
        private final int status;
        private final String contentType;
        private final byte[] body;

        private Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        static Response json(int status, Object value) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return new Response(status, "application/json; charset=UTF-8", mapper.writeValueAsBytes(value));
            } catch (Exception e) {
                return new Response(500, "application/json; charset=UTF-8",
                        ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8));
            }
        }

        public int getStatus() {
            return status;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBody() {
            return body;
        }
    }
}
