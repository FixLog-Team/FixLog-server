package com.fixlog;

import org.springframework.boot.SpringApplication;
import com.fixlog.common.config.AiUsageProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiUsageProperties.class)
public class FixLogApplication {

	public static void main(String[] args) {
		SpringApplication.run(FixLogApplication.class, args);
	}

}
