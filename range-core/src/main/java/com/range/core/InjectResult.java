package com.range.core;

public final class InjectResult {
    private final String caseId;
    private final String server;
    private final String mount;
    private final String tool;
    private final String method;
    private final String packer;
    private final String payloadBase64;
    private final int payloadSize;
    private final boolean defined;
    private final boolean instantiated;
    private final boolean attached;
    private final String error;
    private final String warning;
    private final long elapsedMs;
    private final ConnectionInfo connection;

    public InjectResult(String caseId, String server, String mount, String tool, String method, String packer,
                        String payloadBase64, int payloadSize, boolean defined, boolean instantiated,
                        boolean attached, String error, String warning, long elapsedMs, ConnectionInfo connection) {
        this.caseId = caseId;
        this.server = server;
        this.mount = mount;
        this.tool = tool;
        this.method = method;
        this.packer = packer;
        this.payloadBase64 = payloadBase64;
        this.payloadSize = payloadSize;
        this.defined = defined;
        this.instantiated = instantiated;
        this.attached = attached;
        this.error = error;
        this.warning = warning;
        this.elapsedMs = elapsedMs;
        this.connection = connection;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getServer() {
        return server;
    }

    public String getMount() {
        return mount;
    }

    public String getTool() {
        return tool;
    }

    public String getMethod() {
        return method;
    }

    public String getPacker() {
        return packer;
    }

    public String getPayloadBase64() {
        return payloadBase64;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public boolean isDefined() {
        return defined;
    }

    public boolean isInstantiated() {
        return instantiated;
    }

    public boolean isAttached() {
        return attached;
    }

    public String getError() {
        return error;
    }

    public String getWarning() {
        return warning;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public ConnectionInfo getConnection() {
        return connection;
    }
}
