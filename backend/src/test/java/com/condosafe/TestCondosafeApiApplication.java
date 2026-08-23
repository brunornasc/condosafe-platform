package com.condosafe;

import com.condosafe.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestCondosafeApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CondosafeApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
