package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeySanitizerTest {

    /** Generic placeholder — not a real provider key shape (avoids secret-scan false positives). */
    private static final String SAMPLE_KEY = "exampleSanitizedKey_01noRealSecret";

    @Test
    void sanitize_returnsNull_whenInputIsNull() {
        assertThat(ApiKeySanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_returnsNull_whenInputIsBlank() {
        assertThat(ApiKeySanitizer.sanitize("")).isNull();
        assertThat(ApiKeySanitizer.sanitize("   ")).isNull();
    }

    @Test
    void sanitize_returnsKey_whenClean() {
        assertThat(ApiKeySanitizer.sanitize(SAMPLE_KEY)).isEqualTo(SAMPLE_KEY);
    }

    @Test
    void sanitize_stripsNewlineAndComment() {
        String raw = SAMPLE_KEY + "\n#export EXAMPLE_KEY=" + SAMPLE_KEY;
        assertThat(ApiKeySanitizer.sanitize(raw)).isEqualTo(SAMPLE_KEY);
    }

    @Test
    void sanitize_stripsTrailingWhitespace() {
        assertThat(ApiKeySanitizer.sanitize(SAMPLE_KEY + "  ")).isEqualTo(SAMPLE_KEY);
    }

    @Test
    void sanitize_stripsCommentOnSameLine() {
        assertThat(ApiKeySanitizer.sanitize(SAMPLE_KEY + " # comment")).isEqualTo(SAMPLE_KEY);
    }

    @Test
    void sanitize_usesFirstLine_whenMultipleLines() {
        String raw = SAMPLE_KEY + "\nsecond line\nthird";
        assertThat(ApiKeySanitizer.sanitize(raw)).isEqualTo(SAMPLE_KEY);
    }

    @Test
    void sanitize_handlesCarriageReturn() {
        String raw = SAMPLE_KEY + "\r\n#export";
        assertThat(ApiKeySanitizer.sanitize(raw)).isEqualTo(SAMPLE_KEY);
    }
}
