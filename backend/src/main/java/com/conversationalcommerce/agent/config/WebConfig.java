package com.conversationalcommerce.agent.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Web configuration including CORS and SPA static serving.
 * Set app.cors.allowed-origins for production (e.g. https://your-frontend.com).
 * Set app.serve-frontend=true when frontend assets are in classpath:static/ (e.g. Docker build).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsConfig;

    @Value("${app.serve-frontend:false}")
    private boolean serveFrontend;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = "*".equals(allowedOriginsConfig) || allowedOriginsConfig.isEmpty()
                ? new String[]{"*"}
                : allowedOriginsConfig.split(",\\s*");
        String[] mappings = {"/api/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"};
        for (String mapping : mappings) {
            registry.addMapping(mapping)
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (serveFrontend) {
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/")
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String path, Resource location) throws IOException {
                            Resource r = location.createRelative(path);
                            if (r.exists() && r.isReadable()) return r;
                            return new ClassPathResource("static/index.html");
                        }
                    });
        }
    }
}
