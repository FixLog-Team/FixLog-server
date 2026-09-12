package com.fixlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FixLogApplication {

	public static void main(String[] args) {
		SpringApplication.run(FixLogApplication.class, args);
	}

}
