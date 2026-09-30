package com.wm.semantic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SemanticServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemanticServerApplication.class, args);
        System.out.println("===== ChatBI data-server Started =====");
    }
}