package com.shipmate;

import com.shipmate.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class ShipmateApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmateApplication.class, args);
    }
}
