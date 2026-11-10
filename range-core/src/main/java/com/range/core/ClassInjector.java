package com.range.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public final class ClassInjector {
    private ClassInjector() {
    }

    public static Outcome load(String payload, String packer, String injectorClassName) {
        long start = System.nanoTime();
        boolean defined = false;
        boolean instantiated = false;
        String error = null;
        String warning = null;
        try {
            if ("Jar".equals(packer)) {
                if (injectorClassName == null || injectorClassName.isEmpty()) {
                    throw new IllegalArgumentException("Jar payload requires injectorClassName");
                }
                byte[] jar = Base64.getDecoder().decode(payload);
                Path temp = Files.createTempFile("range-payload-", ".jar");
                Files.write(temp, jar);
                URLClassLoader loader = new URLClassLoader(new URL[]{temp.toUri().toURL()}, parent());
                Class<?> clazz = Class.forName(injectorClassName, false, loader);
                defined = true;
                try {
                    clazz.getDeclaredConstructor().newInstance();
                    instantiated = true;
                } catch (Throwable t) {
                    warning = "defined but init failed: " + t.getClass().getName() + ": " + t.getMessage();
                }
            } else {
                byte[] bytes = decode(payload, packer);
                Class<?> clazz = new ByteArrayLoader(parent()).define(bytes);
                defined = true;
                try {
                    clazz.getDeclaredConstructor().newInstance();
                    instantiated = true;
                } catch (Throwable t) {
                    warning = "defined but init failed: " + t.getClass().getName() + ": " + t.getMessage();
                }
            }
        } catch (Throwable t) {
            error = t.getClass().getName() + ": " + t.getMessage();
        }
        return new Outcome(defined, instantiated, error, warning, (System.nanoTime() - start) / 1000000L);
    }

    private static byte[] decode(String payload, String packer) throws Exception {
        String value = payload;
        if ("Base64URLEncoded".equals(packer)) {
            value = URLDecoder.decode(value, "UTF-8");
        }
        byte[] bytes = Base64.getDecoder().decode(value);
        if ("GzipBase64".equals(packer) || isGzip(bytes)) {
            bytes = gunzip(bytes);
        }
        return bytes;
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    private static byte[] gunzip(byte[] bytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
        try {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static ClassLoader parent() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : ClassInjector.class.getClassLoader();
    }

    private static final class ByteArrayLoader extends ClassLoader {
        ByteArrayLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    public static final class Outcome {
        private final boolean defined;
        private final boolean instantiated;
        private final String error;
        private final String warning;
        private final long elapsedMs;

        Outcome(boolean defined, boolean instantiated, String error, String warning, long elapsedMs) {
            this.defined = defined;
            this.instantiated = instantiated;
            this.error = error;
            this.warning = warning;
            this.elapsedMs = elapsedMs;
        }

        public boolean isDefined() {
            return defined;
        }

        public boolean isInstantiated() {
            return instantiated;
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
    }
}
