package com.example.failureapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FailureAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(FailureAppApplication.class, args);
    }
}
