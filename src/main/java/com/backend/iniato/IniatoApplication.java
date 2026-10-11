package com.backend.iniato;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IniatoApplication {

	public static void main(String[] args) {
		SpringApplication.run(IniatoApplication.class, args);
	}

}
