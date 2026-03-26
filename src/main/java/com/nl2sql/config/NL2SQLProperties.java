package com.nl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NL2SQL 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "nl2sql")
public class NL2SQLProperties {

    private List<DatabaseHost> databases;
    private VolcanoEngine volcanoEngine;
    private Ollama ollama;
    private Model model;
    private Settings settings;
    private Files files;

    @Data
    public static class DatabaseHost {
        private String name;
        private String host;
        private Integer port = 3306;
        private String username;
        private String password;
        private List<String> databases;
        private Pool pool;
        private String dbType = "mysql"; // mysql, oracle
    }

    @Data
    public static class Pool {
        private Integer maximumPoolSize = 10;
        private Integer minimumIdle = 5;
        private Long connectionTimeout = 30000L;
    }

    @Data
    public static class VolcanoEngine {
        private String apiKey;
        private String model;
        private Long timeout;
        private String baseUrl;
    }

    @Data
    public static class Ollama {
        private Boolean enabled = false;
        private String host;
        private String model;
        private Long timeout;
    }

    @Data
    public static class Model {
        private String provider = "volcano_engine";
    }

    @Data
    public static class Settings {
        private Boolean refreshDb = true;
        private Boolean refreshSchema = true;
        private Boolean saveResults = false;
        private Integer maxRetries = 5;
        private Integer cacheMaxSize = 1000;
        private Integer cacheExpireMinutes = 60;
    }

    @Data
    public static class Files {
        private String annotation = "annotations.json";
        private String session = "session.txt";
        private String dbOverview = "db.txt";
        private String schemaDir = ".";
    }
}
