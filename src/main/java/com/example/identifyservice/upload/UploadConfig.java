package com.example.identifyservice.upload;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UploadConfig {
    @Bean
    CloudinaryImageStorage cloudinaryImageStorage(CloudinaryProperties props) {
        return new CloudinaryImageStorage(props);
    }
}
