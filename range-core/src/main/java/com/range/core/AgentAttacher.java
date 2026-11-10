package com.range.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public final class AgentAttacher {
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
            ProcessBuilder builder = new ProcessBuilder(java, "-jar", jar.toString(), ServerDetector.pid());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = read(process.getInputStream());
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Outcome(false, "attach timeout", output, jar.toString(), elapsed(start));
            }
            int code = process.exitValue();
            return new Outcome(code == 0, code == 0 ? null : "attach exit " + code + ": " + output,
                    output, jar.toString(), elapsed(start));
        } catch (Throwable t) {
            return new Outcome(false, t.getClass().getName() + ": " + t.getMessage(),
                    null, jar == null ? null : jar.toString(), elapsed(start));
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

        Outcome(boolean ok, String error, String output, String jarPath, long elapsedMs) {
            this.ok = ok;
            this.error = error;
            this.output = output;
            this.jarPath = jarPath;
            this.elapsedMs = elapsedMs;
        }

        public boolean isOk() {
            return ok;
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
