package com.schwab.urlshortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void encodesZeroAsFirstAlphabetCharacter() {
        assertThat(generator.encode(0)).isEqualTo("0");
    }

    @Test
    void rejectsNegativeIds() {
        assertThatThrownBy(() -> generator.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "61, Z",
            "62, 10",
            "3843, ZZ",       // 62^2 - 1
            "238327, ZZZ"     // 62^3 - 1
    })
    void encodesBoundaryValuesToExpectedBase62(long id, String expectedCode) {
        assertThat(generator.encode(id)).isEqualTo(expectedCode);
    }

    @Test
    void isDeterministicAndRoundTripsUniquelyAcrossARange() {
        // Every id in a reasonable range must produce a unique code - a collision here
        // would mean the encoding itself is broken, independent of DB-level uniqueness.
        var seen = new java.util.HashSet<String>();
        for (long id = 0; id < 100_000; id++) {
            String code = generator.encode(id);
            assertThat(seen.add(code))
                    .as("code %s for id %d should not have been seen before", code, id)
                    .isTrue();
        }
    }
}
