package com.range.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class AgentAttacher {
    private static final int ATTACH_ATTEMPTS = 3;
    private static final long ATTACH_RETRY_DELAY_MS = 500L;

    private AgentAttacher() {
    }

    public static Outcome attach(String payload, String caseId) {
        long start = System.nanoTime();
        Path jar = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            jar = Files.createTempFile("range-agent-" + caseId + "-", ".jar");
            Files.write(jar, bytes);
            String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            Outcome outcome = null;
            for (int attempt = 1; attempt <= ATTACH_ATTEMPTS; attempt++) {
                outcome = runAttacher(java, jar, start);
                if (outcome.isOk() || !outcome.isRetryable()) {
                    return outcome;
                }
                Thread.sleep(ATTACH_RETRY_DELAY_MS);
            }
            return outcome;
        } catch (Throwable t) {
            return new Outcome(false, t.getClass().getName() + ": " + t.getMessage(),
                    null, jar == null ? null : jar.toString(), elapsed(start));
        }
    }

    private static Outcome runAttacher(String java, Path jar, long start)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(java, "-jar", jar.toString(), ServerDetector.pid());
        builder.redirectErrorStream(true);
        // The attacher is a control process, not the protected application.
        // Letting the probe load here registers a second app for the same
        // container and makes the second and later attach handshakes fail.
        stripAgentFromChild(builder.environment());
        Process process = builder.start();
        String output = read(process.getInputStream());
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new Outcome(false, "attach timeout", output, jar.toString(), elapsed(start));
        }
        int code = process.exitValue();
        // The JDK attach handshake fails transiently when the target VM is still
        // tearing down the previous connection; the agent never loads, so a
        // retry cannot double-inject.
        boolean retryable = code != 0 && output.contains("Error during attachment");
        return new Outcome(code == 0, code == 0 ? null : "attach exit " + code + ": " + output,
                output, jar.toString(), elapsed(start), retryable);
    }

    private static void stripAgentFromChild(Map<String, String> env) {
        for (String key : new String[]{"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) {
            String value = env.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            StringBuilder kept = new StringBuilder();
            for (String token : value.trim().split("\\s+")) {
                if (token.startsWith("-javaagent:")) {
                    continue;
                }
                if (kept.length() > 0) {
                    kept.append(' ');
                }
                kept.append(token);
            }
            if (kept.length() == 0) {
                env.remove(key);
            } else {
                env.put(key, kept.toString());
            }
        }
    }

    private static String read(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, n, "UTF-8"));
        }
        return sb.toString();
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1000000L;
    }

    public static final class Outcome {
        private final boolean ok;
        private final String error;
        private final String output;
        private final String jarPath;
        private final long elapsedMs;
        private final boolean retryable;

        Outcome(boolean ok, String error, String output, String jarPath, long elapsedMs) {
            this(ok, error, output, jarPath, elapsedMs, false);
        }

        Outcome(boolean ok, String error, String output, String jarPath, long elapsedMs, boolean retryable) {
            this.ok = ok;
            this.error = error;
            this.output = output;
            this.jarPath = jarPath;
            this.elapsedMs = elapsedMs;
            this.retryable = retryable;
        }

        public boolean isOk() {
            return ok;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public String getError() {
            return error;
        }

        public String getOutput() {
            return output;
        }

        public String getJarPath() {
            return jarPath;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }
}
