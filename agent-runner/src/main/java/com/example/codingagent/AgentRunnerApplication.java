package com.example.codingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentRunnerApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AgentRunnerApplication.class, args)));
    }
}
