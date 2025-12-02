package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.NL2SQLProperties;
import com.nl2sql.model.entity.SessionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 缓存服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final NL2SQLProperties properties;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "keywords", key = "#questionHash")
    public Map<String, List<String>> getCachedKeywords(String questionHash) {
        return null;
    }

    public void setCachedKeywords(String question, Map<String, List<String>> keywords) {
        log.debug("💾 缓存关键词: {}", question.substring(0, Math.min(50, question.length())));
    }

    @Cacheable(value = "sqlResults", key = "#sqlHash")
    public List<Map<String, Object>> getCachedSqlResult(String sqlHash) {
        return null;
    }

    public void setCachedSqlResult(String sql, List<Map<String, Object>> results) {
        log.debug("💾 缓存SQL结果: {}", sql.substring(0, Math.min(50, sql.length())));
    }

    @CacheEvict(value = {"keywords", "sqlResults"}, allEntries = true)
    public void clearAllCache() {
        log.info("🧹 已清除所有缓存");
    }
}
