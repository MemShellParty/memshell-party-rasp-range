package com.range.webflux;

import com.range.core.RangeApi;
import com.range.core.RuntimeInfo;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RangeController {
    private final RangeApi api = new RangeApi(new RuntimeInfo("SpringWebFlux", "unknown", false, "",
            Collections.singletonList("SpringWebFlux")));

    @RequestMapping({"/api/**", "/entrypoint"})
    public Mono<ResponseEntity<byte[]>> handle(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();
        Map<String, String> query = new LinkedHashMap<String, String>();
        request.getQueryParams().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                query.put(key, values.get(0));
            }
        });
        String scheme = request.getURI().getScheme() == null ? "http" : request.getURI().getScheme();
        String authority = request.getURI().getAuthority();
        String baseUrl = authority == null ? "" : scheme + "://" + authority;
        String contentType = request.getHeaders().getContentType() == null
                ? null : request.getHeaders().getContentType().toString();
        return DataBufferUtils.join(request.getBody())
                .map(RangeController::bytes)
                .defaultIfEmpty(new byte[0])
                .map(body -> {
                    RangeApi.Response result = api.handle(request.getMethod().name(), path, query, body,
                            contentType, baseUrl);
                    return ResponseEntity.status(result.getStatus())
                            .contentType(MediaType.parseMediaType(result.getContentType()))
                            .body(result.getBody());
                });
    }

    private static byte[] bytes(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
