package com.range.it;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class SpringMvcJakartaIT {
    @Test
    void injectsEverySpringMvcJakartaType() {
        try (GenericContainer<?> spring = Containers.boot("eclipse-temurin:17.0.17_10-jdk-jammy", "range-springmvc-jakarta.jar")) {
            spring.start();
            RangeAssertions.verifyAll(Containers.baseUrl(spring), "SpringWebMvc");
        }
    }
}
