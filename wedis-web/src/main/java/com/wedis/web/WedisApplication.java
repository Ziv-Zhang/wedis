package com.wedis.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.wedis")
public class WedisApplication {
	public static void main(String[] args) {
		SpringApplication.run(WedisApplication.class, args);
	}
}
