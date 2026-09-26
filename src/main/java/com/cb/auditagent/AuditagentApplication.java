package com.cb.auditagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.model.tool.ToolCallingManager;

@SpringBootApplication
public class AuditagentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditagentApplication.class, args);
	}

	@Bean
	public ToolCallingManager toolCallingManager() {
		return ToolCallingManager.builder().build();
	}
}
