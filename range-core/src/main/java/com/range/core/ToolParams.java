package com.range.core;

import com.reajason.javaweb.memshell.ToolConfigFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ToolParams {
    private static final Map<String, List<ParamSpec>> CACHE = new ConcurrentHashMap<String, List<ParamSpec>>();

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<String, String>();

    static {
        DEFAULTS.put("encryptor", "RAW");
        DEFAULTS.put("implementationClass", "RuntimeExec");
        DEFAULTS.put("template", "{command}");
    }

    private ToolParams() {
    }

    public static List<ParamSpec> forTool(String tool) {
        if (tool == null || tool.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParamSpec> cached = CACHE.get(tool);
        if (cached != null) {
            return cached;
        }
        List<ParamSpec> specs = introspect(tool);
        CACHE.put(tool, specs);
        return specs;
    }

    public static Map<String, List<ParamSpec>> all() {
        Map<String, List<ParamSpec>> all = new LinkedHashMap<String, List<ParamSpec>>();
        for (String tool : Arrays.asList("Godzilla", "GodzillaSession", "Behinder", "Command", "Suo5",
                "AntSword", "NeoreGeorg", "Proxy")) {
            all.put(tool, forTool(tool));
        }
        return Collections.unmodifiableMap(all);
    }

    private static List<ParamSpec> introspect(String tool) {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        try {
            for (ToolConfigFactory.ConfigParam param : ToolConfigFactory.describeParams(tool)) {
                String name = param.getName();
                String label = param.getLabel() == null || param.getLabel().isEmpty() ? name : param.getLabel();
                String defaultValue = defaultValue(tool, param);
                specs.add(new ParamSpec(name, label, defaultValue, param.getOptions()));
            }
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(specs);
    }

    private static String defaultValue(String tool, ToolConfigFactory.ConfigParam param) {
        String name = param.getName();
        if ("headerName".equals(name)) {
            return "NeoreGeorg".equals(tool) ? "Referer" : "User-Agent";
        }
        String value = DEFAULTS.get(name);
        if (value != null) {
            return value;
        }
        if ("string".equals(param.getType()) && param.getPlaceholder() != null && !param.getPlaceholder().isEmpty()) {
            return param.getPlaceholder();
        }
        if ("enum".equals(param.getType()) && !param.getOptions().isEmpty()) {
            return param.getOptions().get(0);
        }
        return "";
    }
}
