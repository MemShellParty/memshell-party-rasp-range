package com.range.core;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

public final class ServerDetector {
    private ServerDetector() {
    }

    public static String detect(String serverInfo) {
        if (serverInfo == null) {
            return "Tomcat";
        }
        String value = serverInfo.toLowerCase();
        if (value.contains("tomcat")) {
            return "Tomcat";
        }
        if (value.contains("jetty")) {
            return "Jetty";
        }
        if (value.contains("undertow")) {
            return "Undertow";
        }
        if (value.contains("weblogic")) {
            return "WebLogic";
        }
        if (value.contains("websphere")) {
            return "WebSphere";
        }
        if (value.contains("glassfish")) {
            return "GlassFish";
        }
        if (value.contains("tongweb")) {
            return "TongWeb";
        }
        if (value.contains("inforsuite")) {
            return "InforSuite";
        }
        if (value.contains("apusic")) {
            return "Apusic";
        }
        if (value.contains("jboss") || value.contains("wildfly")) {
            return "JBoss";
        }
        if (value.contains("resin")) {
            return "Resin";
        }
        if (value.contains("bes")) {
            return "BES";
        }
        return "Tomcat";
    }

    public static String version(String serverInfo) {
        if (serverInfo == null) {
            return "unknown";
        }
        int slash = serverInfo.indexOf('/');
        if (slash < 0 || slash == serverInfo.length() - 1) {
            return "unknown";
        }
        String version = serverInfo.substring(slash + 1).trim();
        return version.isEmpty() ? "unknown" : version;
    }

    public static List<String> servletServers() {
        return Arrays.asList("Tomcat", "Jetty", "Jetty5", "Undertow", "JBoss", "Resin", "Resin2",
                "WebLogic", "WebSphere", "GlassFish", "TongWeb", "BES", "InforSuite", "Apusic");
    }

    public static String pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : name;
    }

    public static int classVersion() {
        String value = System.getProperty("java.class.version", "0");
        int dot = value.indexOf('.');
        try {
            return Integer.parseInt(dot > 0 ? value.substring(0, dot) : value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String javaVersion() {
        return System.getProperty("java.version", "unknown");
    }

    public static boolean agentCapable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            return false;
        }
        return new File(new File(javaHome, "bin"), "java").isFile();
    }
}
