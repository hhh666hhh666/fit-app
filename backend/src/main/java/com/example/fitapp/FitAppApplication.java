package com.example.fitapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.fitapp.**.mapper")
public class FitAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitAppApplication.class, args);
    }
}
