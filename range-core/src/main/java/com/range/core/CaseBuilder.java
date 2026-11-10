package com.range.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CaseBuilder {
    private CaseBuilder() {
    }

    public static ShellCase build(RuntimeInfo runtime, Map<String, String> input) {
        Map<String, String> in = input == null ? new LinkedHashMap<String, String>() : input;
        String server = first(in.get("server"), runtime.getServer());
        String tool = first(in.get("tool"), "");
        String mount = first(in.get("mount"), "");
        String serverVersion = first(in.get("serverVersion"), runtime.getServerVersion());
        serverVersion = ServerDetector.sdkServerVersion(server, serverVersion);
        boolean jakarta = runtime.isJakarta();
        if (in.containsKey("jakarta")) {
            jakarta = Boolean.parseBoolean(in.get("jakarta"));
        }
        boolean agent = mount.startsWith("Agent");
        String seed = server + "|" + mount + "|" + tool + "|" + System.nanoTime();
        String caseId = digest(seed).substring(0, 10);

        String packer = first(in.get("packer"), agent ? "AgentJarWithJDKAttacher" : "DefaultBase64");
        if (agent && !packer.startsWith("AgentJar")) {
            packer = "AgentJarWithJDKAttacher";
        }
        if (!agent && packer.startsWith("AgentJar")) {
            packer = "DefaultBase64";
        }

        String urlPattern = in.get("urlPattern");
        if (urlPattern == null || urlPattern.trim().isEmpty()) {
            if (agent) {
                urlPattern = "";
            } else if (mount.contains("RawWebSocket")) {
                urlPattern = "/__range__/ws/" + caseId;
            } else {
                urlPattern = "/__range__/" + caseId;
            }
        }

        boolean obfuscate = Boolean.parseBoolean(first(in.get("obfuscate"), "false"));
        Map<String, String> params = new LinkedHashMap<String, String>();
        List<ParamSpec> specs = ToolParams.forTool(tool);
        for (ParamSpec spec : specs) {
            String value = in.get(spec.getName());
            if (value == null || value.trim().isEmpty()) {
                value = defaultFor(spec, caseId);
            }
            params.put(spec.getName(), value);
        }

        int jre = runtime.getClassVersion() > 0 ? runtime.getClassVersion() : 52;
        return new ShellCase(caseId, server, serverVersion, jakarta, mount, tool, params, packer,
                urlPattern, obfuscate, agent, jre);
    }

    private static String defaultFor(ParamSpec spec, String caseId) {
        String name = spec.getName();
        if ("pass".equals(name)) {
            return "p" + caseId;
        }
        if ("key".equals(name)) {
            return "k" + caseId;
        }
        if ("headerValue".equals(name)) {
            return "h" + caseId;
        }
        if ("paramName".equals(name)) {
            return "c" + caseId;
        }
        if ("template".equals(name)) {
            return "{command}";
        }
        return spec.getDefaultValue();
    }

    private static String first(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(System.nanoTime());
        }
    }
}
