package com.range.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class RangeAssertions {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private RangeAssertions() {
    }

    public static File artifact(String name) {
        File file = new File(System.getProperty("artifacts.dir"), name);
        assertTrue(file.isFile(), "artifact not found: " + file.getAbsolutePath());
        return file;
    }

    public static void verifyAll(String baseUrl, String server) {
        JsonNode catalog = get(baseUrl + "/api/catalog?server=" + server);
        assertFalse(catalog.has("error") && catalog.get("error").isTextual(),
                "catalog error: " + catalog.path("error").asText());
        JsonNode matrix = catalog.path("matrix");
        assertTrue(matrix.size() > 0, "empty catalog matrix for " + server);

        List<String> failures = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, JsonNode> tool : iterable(matrix)) {
            String toolName = tool.getKey();
            for (JsonNode mount : tool.getValue()) {
                total++;
                Map<String, String> body = new LinkedHashMap<>();
                body.put("server", server);
                body.put("tool", toolName);
                body.put("mount", mount.asText());
                JsonNode result = postJson(baseUrl + "/api/inject", body);
                boolean ok = result.path("defined").asBoolean(false) || result.path("attached").asBoolean(false);
                if (!ok) {
                    failures.add(toolName + "/" + mount.asText()
                            + " error=" + result.path("error").asText("")
                            + " warning=" + result.path("warning").asText(""));
                }
            }
        }
        System.out.println("verified " + total + " combinations for " + server
                + ", failures=" + failures.size());
        if (!failures.isEmpty()) {
            fail("injection failures:\n  " + String.join("\n  ", failures));
        }
        assertTrue(total > 0, "no combinations executed for " + server);
    }

    public static JsonNode get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "GET " + url + " -> " + response.body());
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("GET " + url + " failed", e);
        }
    }

    public static JsonNode postJson(String url, Map<String, String> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "POST " + url + " -> " + response.body());
            JsonNode node = MAPPER.readTree(response.body());
            assertFalse(node.has("error") && node.get("error").isTextual(),
                    "POST " + url + " error: " + node.path("error").asText());
            return node;
        } catch (Exception e) {
            throw new IllegalStateException("POST " + url + " failed", e);
        }
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterable(JsonNode node) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        node.fields().forEachRemaining(entries::add);
        return entries;
    }
}
