package com.nl2sql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NL2SQL 主应用程序
 * 
 * @author NL2SQL Team
 * @version 4.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class NL2SQLApplication {

    public static void main(String[] args) {
        SpringApplication.run(NL2SQLApplication.class, args);
        System.out.println("=".repeat(60));
        System.out.println("🚀 NL2SQL Service Started Successfully!");
        System.out.println("📡 Main Service: http://localhost:8080/api");
        System.out.println("📚 API Docs: http://localhost:8080/api/swagger-ui.html");
        System.out.println("=".repeat(60));
    }
}
