package com.range.core;

import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.PackerRegistry;
import com.reajason.javaweb.memshell.ShellTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CatalogService {
    private final MemShellGenerator generator = new MemShellGenerator();

    public Map<String, Object> catalog(RuntimeInfo runtime, String requestedServer) {
        Map<String, Map<String, List<String>>> all = generator.getShellTypeCatalog().asMap();
        List<String> servers = new ArrayList<String>();
        for (String server : runtime.getServers()) {
            if (all.containsKey(server)) {
                servers.add(server);
            }
        }
        if (servers.isEmpty() && all.containsKey(runtime.getServer())) {
            servers.add(runtime.getServer());
        }
        String server = servers.contains(requestedServer) ? requestedServer
                : (servers.contains(runtime.getServer()) ? runtime.getServer()
                : (servers.isEmpty() ? runtime.getServer() : servers.get(0)));

        Map<String, List<String>> matrix = new LinkedHashMap<String, List<String>>();
        Map<String, List<String>> serverTools = all.get(server);
        if (serverTools != null) {
            for (Map.Entry<String, List<String>> entry : serverTools.entrySet()) {
                if (ShellTool.Custom.equals(entry.getKey())) {
                    continue;
                }
                List<String> mounts = canonical(server, entry.getValue(), runtime.isJakarta());
                if (!mounts.isEmpty()) {
                    matrix.put(entry.getKey(), mounts);
                }
            }
        }

        Set<String> tools = new LinkedHashSet<String>(matrix.keySet());
        Map<String, List<ParamSpec>> toolParams = new LinkedHashMap<String, List<ParamSpec>>();
        for (String tool : tools) {
            toolParams.put(tool, ToolParams.forTool(tool));
        }

        Map<String, Object> catalog = new LinkedHashMap<String, Object>();
        catalog.put("runtime", runtime);
        catalog.put("servers", servers);
        catalog.put("server", server);
        catalog.put("matrix", matrix);
        catalog.put("toolParams", toolParams);
        catalog.put("packers", supportedPackers());
        return catalog;
    }

    public List<String> mounts(String server, String tool, RuntimeInfo runtime) {
        Map<String, Map<String, List<String>>> all = generator.getShellTypeCatalog().asMap();
        Map<String, List<String>> tools = all.get(server);
        if (tools == null) {
            return new ArrayList<String>();
        }
        List<String> raw = tools.get(tool);
        return raw == null ? new ArrayList<String>() : canonical(server, raw, runtime.isJakarta());
    }

    private static List<String> canonical(String server, List<String> raw, boolean jakarta) {
        List<String> mounts = new ArrayList<String>();
        for (String mount : raw) {
            boolean prefixed = mount.startsWith("Jakarta");
            if (!jakarta && prefixed) {
                continue;
            }
            if (jakarta && prefixed && raw.contains(mount.substring("Jakarta".length()))) {
                continue;
            }
            mounts.add(mount);
        }
        return mounts;
    }

    private static List<String> supportedPackers() {
        PackerRegistry registry = PackerRegistry.getInstance();
        List<String> packers = new ArrayList<String>();
        for (String name : new String[]{"DefaultBase64", "GzipBase64", "Base64URLEncoded", "Jar",
                "AgentJarWithJDKAttacher", "AgentJarWithJREAttacher", "FilelessAgent"}) {
            if (registry.contains(name)) {
                packers.add(name);
            }
        }
        return packers;
    }
}
