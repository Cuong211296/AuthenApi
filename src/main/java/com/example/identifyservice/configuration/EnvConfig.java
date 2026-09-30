package com.example.identifyservice.configuration;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EnvConfig implements EnvironmentPostProcessor {

    private static final String ENV_FILE = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        loadEnvFile(environment);
    }

    private void loadEnvFile(ConfigurableEnvironment environment) {
        File envFile = new File(ENV_FILE);

        // If .env file doesn't exist, skip loading
        if (!envFile.exists()) {
            System.out.println("[INFO] .env file not found. Using default or system environment variables.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(envFile)) {
            Properties properties = new Properties();
            properties.load(fis);

            Map<String, Object> envMap = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                // Skip comments and empty lines
                if (!key.startsWith("#") && !key.isEmpty() && value != null) {
                    envMap.put(key, value);
                }
            }

            // Add as property source with low priority (system env vars take precedence)
            MapPropertySource envPropertySource = new MapPropertySource("env-file", envMap);
            environment.getPropertySources().addLast(envPropertySource);

            System.out.println("[INFO] Loaded " + envMap.size() + " properties from .env file");

        } catch (IOException e) {
            System.out.println("[WARN] Failed to load .env file: " + e.getMessage());
        }
    }
}
