package com.range.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeInfo {
    private final String server;
    private final String serverVersion;
    private final boolean jakarta;
    private final String javaVersion;
    private final int classVersion;
    private final String pid;
    private final String basePath;
    private final boolean agentCapable;
    private final List<String> servers;

    public RuntimeInfo(String server, String serverVersion, boolean jakarta, String basePath, List<String> servers) {
        this.server = server;
        this.serverVersion = serverVersion;
        this.jakarta = jakarta;
        this.javaVersion = ServerDetector.javaVersion();
        this.classVersion = ServerDetector.classVersion();
        this.pid = ServerDetector.pid();
        this.basePath = basePath == null ? "" : basePath;
        this.agentCapable = ServerDetector.agentCapable();
        this.servers = Collections.unmodifiableList(new ArrayList<String>(servers));
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

    public String getJavaVersion() {
        return javaVersion;
    }

    public int getClassVersion() {
        return classVersion;
    }

    public String getPid() {
        return pid;
    }

    public String getBasePath() {
        return basePath;
    }

    public boolean isAgentCapable() {
        return agentCapable;
    }

    public List<String> getServers() {
        return servers;
    }
}
