package com.range.core;

import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.PackerRegistry;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ObfuscationConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InjectService {
    private final MemShellGenerator generator = new MemShellGenerator();
    private final List<InjectResult> history = Collections.synchronizedList(new ArrayList<InjectResult>());

    public GenerateResult generate(ShellCase shellCase, String baseUrl) {
        try {
            MemShellResult result = build(shellCase);
            String payload = PackerRegistry.getInstance().pack(shellCase.getPacker(), result);
            return new GenerateResult(shellCase.getCaseId(), shellCase.getServer(), shellCase.getMount(),
                    shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(), payload,
                    payload == null ? 0 : payload.length(), null,
                    ConnectionInfo.from(shellCase, baseUrl, result.getShellClassName(), result.getInjectorClassName()),
                    result.getShellClassName(), result.getInjectorClassName());
        } catch (Throwable t) {
            return new GenerateResult(shellCase.getCaseId(), shellCase.getServer(), shellCase.getMount(),
                    shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(), null, 0,
                    describe(t), ConnectionInfo.from(shellCase, baseUrl));
        }
    }

    public InjectResult inject(ShellCase shellCase, String baseUrl) {
        GenerateResult generated = generate(shellCase, baseUrl);
        if (generated.getError() != null) {
            return new InjectResult(shellCase.getCaseId(), shellCase.getServer(), shellCase.getMount(),
                    shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(), null, 0,
                    false, false, false, generated.getError(), null, 0, generated.getConnection());
        }
        InjectResult result = injectPayload(shellCase, generated.getPayloadBase64(), baseUrl,
                generated.getInjectorClassName(), generated.getConnection());
        history.add(result);
        return result;
    }

    public InjectResult injectPayload(ShellCase shellCase, String payload, String baseUrl) {
        return injectPayload(shellCase, payload, baseUrl, null, ConnectionInfo.from(shellCase, baseUrl));
    }

    private InjectResult injectPayload(ShellCase shellCase, String payload, String baseUrl,
                                       String injectorClassName, ConnectionInfo connection) {
        if (payload == null || payload.trim().isEmpty()) {
            return new InjectResult(shellCase.getCaseId(), shellCase.getServer(), shellCase.getMount(),
                    shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(), null, 0,
                    false, false, false, "empty payload", null, 0, connection);
        }
        long start = System.nanoTime();
        InjectResult result;
        if (shellCase.isAgent()) {
            AgentAttacher.Outcome outcome = AgentAttacher.attach(payload, shellCase.getCaseId());
            result = new InjectResult(shellCase.getCaseId(), shellCase.getServer(), shellCase.getMount(),
                    shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(), payload, payload.length(),
                    false, false, outcome.isOk(), outcome.getError(),
                    outcome.isOk() ? null : snippet(outcome.getOutput()),
                    (System.nanoTime() - start) / 1000000L, connection);
        } else {
            ClassInjector.Outcome outcome = ClassInjector.load(payload, shellCase.getPacker(),
                    injectorClassName);
            result = new InjectResult(shellCase.getCaseId(), shellCase.getServer(), shellCase.getMount(),
                    shellCase.getTool(), shellCase.getMethod(), shellCase.getPacker(), payload, payload.length(),
                    outcome.isDefined(), outcome.isInstantiated(), false, outcome.getError(), outcome.getWarning(),
                    (System.nanoTime() - start) / 1000000L, connection);
        }
        logOutcome(shellCase, result);
        history.add(result);
        return result;
    }

    public List<InjectResult> history() {
        synchronized (history) {
            return new ArrayList<InjectResult>(history);
        }
    }

    public void clearHistory() {
        history.clear();
    }

    private MemShellResult build(ShellCase shellCase) {
        ShellConfig shellConfig = ShellConfig.builder()
                .server(shellCase.getServer())
                .serverVersion(shellCase.getServerVersion())
                .shellTool(shellCase.getTool())
                .shellType(shellCase.getMount())
                .jakarta(shellCase.isJakarta())
                .targetJreVersion(shellCase.getTargetJreVersion())
                .obfuscation(shellCase.isObfuscate() ? ObfuscationConfig.enabled() : ObfuscationConfig.defaults())
                .build();
        // Class names stay unset so the SDK samples them per generation.
        InjectorConfig injectorConfig = InjectorConfig.builder()
                .urlPattern(shellCase.getUrlPattern())
                .build();
        Map<String, String> params = new LinkedHashMap<String, String>(shellCase.getParams());
        return generator.generate(shellConfig, injectorConfig, params);
    }

    private static void logOutcome(ShellCase shellCase, InjectResult result) {
        StringBuilder line = new StringBuilder("[range] ")
                .append(shellCase.getCaseId()).append(' ')
                .append(shellCase.getTool()).append('/').append(shellCase.getMount());
        if (result.isAttached()) {
            line.append(" attached");
        } else if (result.isDefined()) {
            line.append(" defined");
        } else {
            line.append(" failed");
        }
        line.append(" in ").append(result.getElapsedMs()).append("ms");
        if (result.getError() != null && !result.getError().isEmpty()) {
            line.append(" error=").append(result.getError());
        }
        System.out.println(line);
        System.out.flush();
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static String snippet(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
    }
}
