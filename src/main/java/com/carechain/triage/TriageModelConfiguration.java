package com.carechain.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TriageModelConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "carechain.triage.model")
    public TriageModelProperties triageModelProperties() {
        return new TriageModelProperties();
    }

    @Bean
    public TriageModelTrainer triageModelTrainer() {
        return new TriageModelTrainer();
    }
}
