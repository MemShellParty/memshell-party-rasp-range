package com.range.core;

public final class GenerateResult {
    private final String caseId;
    private final String server;
    private final String mount;
    private final String tool;
    private final String method;
    private final String packer;
    private final String payloadBase64;
    private final int payloadSize;
    private final String error;
    private final ConnectionInfo connection;
    private final String shellClassName;
    private final String injectorClassName;

    public GenerateResult(String caseId, String server, String mount, String tool, String method, String packer,
                          String payloadBase64, int payloadSize, String error, ConnectionInfo connection) {
        this(caseId, server, mount, tool, method, packer, payloadBase64, payloadSize, error, connection,
                null, null);
    }

    public GenerateResult(String caseId, String server, String mount, String tool, String method, String packer,
                          String payloadBase64, int payloadSize, String error, ConnectionInfo connection,
                          String shellClassName, String injectorClassName) {
        this.caseId = caseId;
        this.server = server;
        this.mount = mount;
        this.tool = tool;
        this.method = method;
        this.packer = packer;
        this.payloadBase64 = payloadBase64;
        this.payloadSize = payloadSize;
        this.error = error;
        this.connection = connection;
        this.shellClassName = shellClassName;
        this.injectorClassName = injectorClassName;
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

    public String getError() {
        return error;
    }

    public ConnectionInfo getConnection() {
        return connection;
    }

    public String getShellClassName() {
        return shellClassName;
    }

    public String getInjectorClassName() {
        return injectorClassName;
    }
}
