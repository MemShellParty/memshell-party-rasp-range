package com.range.it;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

public final class Containers {
    private Containers() {
    }

    public static GenericContainer<?> tomcat(String image, String warName) {
        return new GenericContainer<>(DockerImageName.parse(image))
                .withCopyFileToContainer(MountableFile.forHostPath(RangeAssertions.artifact(warName).toPath()),
                        "/usr/local/tomcat/webapps/range.war")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/range/api/runtime")
                        .forPort(8080)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(4)));
    }

    public static GenericContainer<?> boot(String image, String jarName) {
        return new GenericContainer<>(DockerImageName.parse(image))
                .withCopyFileToContainer(MountableFile.forHostPath(RangeAssertions.artifact(jarName).toPath()),
                        "/app/range.jar")
                .withCommand("java", "-jar", "/app/range.jar")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/api/runtime")
                        .forPort(8080)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(4)));
    }

    public static String baseUrl(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(8080);
    }

    public static GenericContainer<?> jetty(String image, String warName) {
        return new GenericContainer<>(DockerImageName.parse(image))
                .withCopyFileToContainer(MountableFile.forHostPath(RangeAssertions.artifact(warName).toPath()),
                        "/var/lib/jetty/webapps/range.war")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/range/api/runtime")
                        .forPort(8080)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(4)));
    }

    public static String servletBaseUrl(GenericContainer<?> container) {
        return baseUrl(container) + "/range";
    }
}
