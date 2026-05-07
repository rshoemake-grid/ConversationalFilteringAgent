package com.conversationalcommerce.agent.config;

/**
 * Sanitizes API keys that may contain newlines, comments, or trailing text
 * (e.g. from copying a full line like "export GOOGLE_API_KEY=...")
 */
public final class ApiKeySanitizer {

    private ApiKeySanitizer() {}

    /**
     * Sanitizes an API key by removing newlines, comments, and surrounding whitespace.
     * Handles values like "key\n#export GOOGLE_API_KEY=key" or "key  ".
     *
     * @param raw the raw API key (may be null)
     * @return trimmed key with only the first line and no comment, or null if input is null/blank
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String firstLine = raw.split("[\r\n]")[0].trim();
        int hashIndex = firstLine.indexOf('#');
        if (hashIndex >= 0) {
            firstLine = firstLine.substring(0, hashIndex).trim();
        }
        return firstLine.isBlank() ? null : firstLine;
    }
}
