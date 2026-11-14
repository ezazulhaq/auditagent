package com.cb.auditagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditagentApplication {

	static {
		if (System.getProperty("langchain4j.http.clientBuilderFactory") == null) {
			System.setProperty("langchain4j.http.clientBuilderFactory", "dev.langchain4j.http.client.okhttp.OkHttpClientBuilderFactory");
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(AuditagentApplication.class, args);
	}
}
