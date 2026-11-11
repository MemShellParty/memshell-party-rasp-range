package com.range.it;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class Jetty9JavaxIT {
    @Test
    void injectsEveryJettyJavaxType() {
        try (GenericContainer<?> jetty = Containers.jetty("jetty:9.4.58-jdk8-eclipse-temurin",
                "range-war-javax.war")) {
            jetty.start();
            RangeAssertions.verifyAll(Containers.servletBaseUrl(jetty), "Jetty");
        }
    }
}
