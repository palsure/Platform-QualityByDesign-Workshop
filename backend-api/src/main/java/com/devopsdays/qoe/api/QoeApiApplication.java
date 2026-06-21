package com.devopsdays.qoe.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class QoeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(QoeApiApplication.class, args);
    }
}
