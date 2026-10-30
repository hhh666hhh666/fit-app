package com.example.fitapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.fitapp.**.mapper")
@EnableScheduling
public class FitAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitAppApplication.class, args);
    }
}
