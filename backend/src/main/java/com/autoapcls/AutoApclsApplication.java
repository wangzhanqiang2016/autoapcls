package com.autoapcls;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutoApclsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoApclsApplication.class, args);
    }
}
