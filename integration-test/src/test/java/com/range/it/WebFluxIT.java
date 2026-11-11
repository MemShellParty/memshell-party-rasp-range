package com.range.it;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class WebFluxIT {
    @Test
    void injectsEveryWebFluxType() {
        try (GenericContainer<?> webflux = Containers.boot("eclipse-temurin:17.0.17_10-jdk-jammy", "range-webflux.jar")) {
            webflux.start();
            RangeAssertions.verifyAll(Containers.baseUrl(webflux), "SpringWebFlux");
        }
    }
}
