package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeySanitizerTest {

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
        String key = "AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug";
        assertThat(ApiKeySanitizer.sanitize(key)).isEqualTo(key);
    }

    @Test
    void sanitize_stripsNewlineAndComment() {
        String raw = "AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug\n#export GOOGLE_API_KEY=AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug";
        assertThat(ApiKeySanitizer.sanitize(raw)).isEqualTo("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug");
    }

    @Test
    void sanitize_stripsTrailingWhitespace() {
        assertThat(ApiKeySanitizer.sanitize("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug  ")).isEqualTo("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug");
    }

    @Test
    void sanitize_stripsCommentOnSameLine() {
        assertThat(ApiKeySanitizer.sanitize("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug # comment")).isEqualTo("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug");
    }

    @Test
    void sanitize_usesFirstLine_whenMultipleLines() {
        String raw = "AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug\nsecond line\nthird";
        assertThat(ApiKeySanitizer.sanitize(raw)).isEqualTo("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug");
    }

    @Test
    void sanitize_handlesCarriageReturn() {
        String raw = "AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug\r\n#export";
        assertThat(ApiKeySanitizer.sanitize(raw)).isEqualTo("AIzaSyCaQqOtU3fVcQfqbXXmo3o9LEve1reNOug");
    }
}
