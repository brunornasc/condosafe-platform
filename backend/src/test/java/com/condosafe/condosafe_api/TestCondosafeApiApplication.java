package com.condosafe.condosafe_api;

import com.condosafe.CondosafeApiApplication;
import org.springframework.boot.SpringApplication;

public class TestCondosafeApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CondosafeApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
