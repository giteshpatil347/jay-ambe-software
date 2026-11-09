package com.materialbook.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternTest {
    @Test
    void escapesWildcardsAndBackslashesAndLowerCases() {
        assertThat(LikePattern.contains("  50%_Off\\X ")).isEqualTo("%50\\%\\_off\\\\x%");
    }
}
