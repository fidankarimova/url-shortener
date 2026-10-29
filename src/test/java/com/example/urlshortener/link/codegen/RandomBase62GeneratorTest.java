package com.example.urlshortener.link.codegen;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RandomBase62GeneratorTest {

    private final RandomBase62Generator generator = new RandomBase62Generator();

    @Test
    void generatesCodeOfExpectedLength() {
        assertThat(generator.generate()).hasSize(7);
    }

    @Test
    void usesOnlyBase62Characters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    void generatesDifferentCodes() {
        Set<String> codes = IntStream.range(0, 1000)
                .mapToObj(i -> generator.generate())
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(1000);
    }
}
