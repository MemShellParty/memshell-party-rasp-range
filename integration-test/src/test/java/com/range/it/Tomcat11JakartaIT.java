package com.range.it;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class Tomcat11JakartaIT {
    @Test
    void injectsEveryTomcatJakartaType() {
        try (GenericContainer<?> tomcat = Containers.tomcat("tomcat:11.0.27-jdk17-temurin-jammy", "range-war-jakarta.war")) {
            tomcat.start();
            RangeAssertions.verifyAll(Containers.tomcatBaseUrl(tomcat), "Tomcat");
        }
    }
}
