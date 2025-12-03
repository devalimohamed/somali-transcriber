package com.somtranscriber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SomTranscriberApplication {

    public static void main(String[] args) {
        SpringApplication.run(SomTranscriberApplication.class, args);
    }
}
