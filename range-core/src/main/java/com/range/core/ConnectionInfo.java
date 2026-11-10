package com.range.core;

public final class ConnectionInfo {
    private final String url;
    private final String urlPattern;
    private final String pass;
    private final String key;
    private final String headerName;
    private final String headerValue;
    private final String paramName;
    private final String injectorClassName;
    private final String shellClassName;
    private final String trigger;

    public ConnectionInfo(String url, String urlPattern, String pass, String key, String headerName,
                          String headerValue, String paramName, String injectorClassName, String shellClassName,
                          String trigger) {
        this.url = url;
        this.urlPattern = urlPattern;
        this.pass = pass;
        this.key = key;
        this.headerName = headerName;
        this.headerValue = headerValue;
        this.paramName = paramName;
        this.injectorClassName = injectorClassName;
        this.shellClassName = shellClassName;
        this.trigger = trigger;
    }

    public static ConnectionInfo from(ShellCase shellCase, String baseUrl) {
        return from(shellCase, baseUrl, null, null);
    }

    /**
     * Class names come from the generated {@code MemShellResult}: the SDK samples them per
     * generation, so they are only known after the shell is built.
     */
    public static ConnectionInfo from(ShellCase shellCase, String baseUrl, String shellClassName,
                                      String injectorClassName) {
        String headerName = shellCase.getParams().get("headerName");
        String headerValue = shellCase.getParams().get("headerValue");
        String paramName = shellCase.getParams().get("paramName");
        String trigger = headerName != null ? headerName + ": " + headerValue
                : (paramName != null ? paramName : "");
        String url = shellCase.isAgent() ? "任意业务路径"
                : (baseUrl == null ? "" : baseUrl) + shellCase.getUrlPattern();
        return new ConnectionInfo(url, shellCase.getUrlPattern(), shellCase.getParams().get("pass"),
                shellCase.getParams().get("key"), headerName, headerValue, paramName,
                injectorClassName, shellClassName, trigger);
    }

    public String getUrl() {
        return url;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public String getPass() {
        return pass;
    }

    public String getKey() {
        return key;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getHeaderValue() {
        return headerValue;
    }

    public String getParamName() {
        return paramName;
    }

    public String getInjectorClassName() {
        return injectorClassName;
    }

    public String getShellClassName() {
        return shellClassName;
    }

    public String getTrigger() {
        return trigger;
    }
}
