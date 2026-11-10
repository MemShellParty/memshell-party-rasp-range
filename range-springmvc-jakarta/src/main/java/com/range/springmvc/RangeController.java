package com.range.springmvc;

import com.range.core.RangeApi;
import com.range.core.RuntimeInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@RestController
public class RangeController {
    private final RangeApi api = new RangeApi(new RuntimeInfo("SpringWebMvc", "unknown", true, "",
            Arrays.asList("SpringWebMvc", "Tomcat", "Jetty", "Undertow")));

    @RequestMapping({"/api/**", "/entrypoint"})
    public ResponseEntity<byte[]> handle(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Map<String, String> query = RangeApi.query(request.getQueryString());
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String baseUrl = scheme + "://" + host + (defaultPort ? "" : ":" + port) + request.getContextPath();
        RangeApi.Response result = api.handle(request.getMethod(), path, query, body,
                request.getContentType(), baseUrl);
        return ResponseEntity.status(result.getStatus())
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .body(result.getBody());
    }
}
