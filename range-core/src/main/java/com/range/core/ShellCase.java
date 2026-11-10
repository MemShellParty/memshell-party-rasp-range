package com.range.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShellCase {
    private final String caseId;
    private final String server;
    private final String serverVersion;
    private final boolean jakarta;
    private final String mount;
    private final String tool;
    private final Map<String, String> params;
    private final String packer;
    private final String urlPattern;
    private final String shellClassName;
    private final String injectorClassName;
    private final boolean agent;
    private final int targetJreVersion;

    public ShellCase(String caseId, String server, String serverVersion, boolean jakarta, String mount, String tool,
                     Map<String, String> params, String packer, String urlPattern, String shellClassName,
                     String injectorClassName, boolean agent, int targetJreVersion) {
        this.caseId = caseId;
        this.server = server;
        this.serverVersion = serverVersion;
        this.jakarta = jakarta;
        this.mount = mount;
        this.tool = tool;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<String, String>(params));
        this.packer = packer;
        this.urlPattern = urlPattern;
        this.shellClassName = shellClassName;
        this.injectorClassName = injectorClassName;
        this.agent = agent;
        this.targetJreVersion = targetJreVersion;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getServer() {
        return server;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public boolean isJakarta() {
        return jakarta;
    }

    public String getMount() {
        return mount;
    }

    public String getTool() {
        return tool;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String getPacker() {
        return packer;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public String getShellClassName() {
        return shellClassName;
    }

    public String getInjectorClassName() {
        return injectorClassName;
    }

    public boolean isAgent() {
        return agent;
    }

    public int getTargetJreVersion() {
        return targetJreVersion;
    }

    public String getMethod() {
        return agent ? "agent" : "class";
    }
}
