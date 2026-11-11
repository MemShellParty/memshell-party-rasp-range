package com.range.it;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class Tomcat9JavaxIT {
    @Test
    void injectsEveryTomcatJavaxType() {
        try (GenericContainer<?> tomcat = Containers.tomcat("tomcat:9.0.123-jdk8-temurin-jammy", "range-war-javax.war")) {
            tomcat.start();
            RangeAssertions.verifyAll(Containers.tomcatBaseUrl(tomcat), "Tomcat");
        }
    }
}
