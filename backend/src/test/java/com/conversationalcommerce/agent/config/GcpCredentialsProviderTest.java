package com.conversationalcommerce.agent.config;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GcpCredentialsProviderTest {

    @Test
    void getCredentials_fallsBackToApplicationDefaultWhenPathEmpty() throws Exception {
        var provider = new GcpCredentialsProvider("", "");
        GoogleCredentials creds = provider.getCredentials();
        assertThat(creds).isNotNull();
    }

    @Test
    void getCredentials_fallsBackToApplicationDefaultWhenPathDoesNotExist() throws Exception {
        var provider = new GcpCredentialsProvider("/nonexistent/path/to/creds.json", "");
        GoogleCredentials creds = provider.getCredentials();
        assertThat(creds).isNotNull();
    }

    @Test
    void getCredentials_usesFileWhenPathExistsAndIsReadable() throws Exception {
        Path tempFile = Files.createTempFile("test-creds-", ".json");
        try {
            Files.writeString(tempFile, """
                    {"type":"authorized_user","client_id":"x","client_secret":"y","refresh_token":"z"}
                    """);
            var provider = new GcpCredentialsProvider(tempFile.toString(), "");
            GoogleCredentials creds = provider.getCredentials();
            assertThat(creds).isNotNull();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void getQuotaProject_returnsConfiguredValue() {
        var provider = new GcpCredentialsProvider("", "my-quota-project");
        assertThat(provider.getQuotaProject()).isEqualTo("my-quota-project");
    }

    @Test
    void getQuotaProject_returnsEmptyWhenNotConfigured() {
        var provider = new GcpCredentialsProvider("", "");
        assertThat(provider.getQuotaProject()).isEmpty();
    }
}
