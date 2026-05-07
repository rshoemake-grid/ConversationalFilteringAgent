package com.conversationalcommerce.agent.config;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provides GCP credentials from a configurable path or Application Default Credentials.
 */
@Component
public class GcpCredentialsProvider {

    private static final String QUOTA_PROJECT_HEADER = "x-goog-user-project";

    private final String credentialsPath;
    private final String quotaProject;

    public GcpCredentialsProvider(
            @Value("${app.gcp.credentials-path:}") String credentialsPath,
            @Value("${app.gcp.quota-project:}") String quotaProject) {
        this.credentialsPath = credentialsPath != null ? credentialsPath.trim() : "";
        this.quotaProject = quotaProject != null ? quotaProject.trim() : "";
    }

    /**
     * Returns credentials from the configured path if it exists and is readable,
     * otherwise falls back to Application Default Credentials.
     */
    public GoogleCredentials getCredentials() throws Exception {
        GoogleCredentials creds;
        if (!credentialsPath.isEmpty()) {
            Path path = Path.of(credentialsPath);
            if (Files.isRegularFile(path) && Files.isReadable(path)) {
                try (InputStream is = new FileInputStream(path.toFile())) {
                    creds = GoogleCredentials.fromStream(is);
                }
            } else {
                creds = GoogleCredentials.getApplicationDefault();
            }
        } else {
            creds = GoogleCredentials.getApplicationDefault();
        }
        if (!quotaProject.isEmpty()) {
            creds = creds.createWithQuotaProject(quotaProject);
        }
        return creds;
    }

    /**
     * Returns the quota project ID for API requests (billing/quota). Empty if not configured.
     */
    public String getQuotaProject() {
        return quotaProject;
    }

    /**
     * Returns the header name for quota project when making raw HTTP requests.
     */
    public static String quotaProjectHeaderName() {
        return QUOTA_PROJECT_HEADER;
    }
}
