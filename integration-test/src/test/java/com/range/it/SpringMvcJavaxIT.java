package com.range.it;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class SpringMvcJavaxIT {
    @Test
    void injectsEverySpringMvcJavaxType() {
        try (GenericContainer<?> spring = Containers.boot("eclipse-temurin:11.0.33_10-jdk-jammy", "range-springmvc-javax.jar")) {
            spring.start();
            RangeAssertions.verifyAll(Containers.baseUrl(spring), "SpringWebMvc");
        }
    }
}
