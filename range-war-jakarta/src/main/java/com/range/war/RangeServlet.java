package com.range.war;

import com.range.core.RangeApi;
import com.range.core.RuntimeInfo;
import com.range.core.ServerDetector;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class RangeServlet extends HttpServlet {
    private RangeApi api;

    @Override
    public void init() {
        String serverInfo = getServletContext().getServerInfo();
        String contextPath = getServletContext().getContextPath();
        RuntimeInfo runtime = new RuntimeInfo(ServerDetector.detect(serverInfo),
                ServerDetector.version(serverInfo), true, contextPath, ServerDetector.servletServers());
        this.api = new RangeApi(runtime);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Map<String, String> query = RangeApi.query(request.getQueryString());
        byte[] body = read(request.getInputStream());
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String baseUrl = scheme + "://" + host + (defaultPort ? "" : ":" + port) + request.getContextPath();
        RangeApi.Response result = api.handle(request.getMethod(), path, query, body,
                request.getContentType(), baseUrl);
        response.setStatus(result.getStatus());
        response.setContentType(result.getContentType());
        response.setContentLength(result.getBody().length);
        response.getOutputStream().write(result.getBody());
        response.getOutputStream().flush();
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
