package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.User;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用户数据库访问作用域服务
 *
 * 说明：当前以数据库级权限控制为主，后续可在此扩展到表级权限控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseAccessScopeService {

    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 获取用户可用的激活主机配置（严格模式，不包含owner为空的历史数据）
     */
    public List<DatabaseHostConfig> getActiveHostConfigsForUser(Integer userId) {
        validateActiveUser(userId);
        return databaseHostConfigRepository.findByOwnerUserIdAndIsActiveTrue(userId);
    }

    /**
     * 获取用户可用的激活主机配置（可选包含owner为空的过渡期配置）
     */
    public List<DatabaseHostConfig> getActiveHostConfigsForUser(Integer userId, boolean includeLegacyUnowned) {
        validateActiveUser(userId);

        List<DatabaseHostConfig> scopedHosts =
            new ArrayList<>(databaseHostConfigRepository.findByOwnerUserIdAndIsActiveTrue(userId));

        if (includeLegacyUnowned) {
            scopedHosts.addAll(databaseHostConfigRepository.findByOwnerUserIdIsNullAndIsActiveTrue());
        }

        return scopedHosts;
    }

    /**
     * 获取用户可访问的数据库名集合（去重后保序）
     */
    public List<String> getAllowedDatabases(Integer userId) {
        return getAllowedDatabases(userId, false);
    }

    /**
     * 获取用户可访问的数据库名集合（去重后保序）
     */
    public List<String> getAllowedDatabases(Integer userId, boolean includeLegacyUnowned) {
        List<DatabaseHostConfig> scopedHosts = getActiveHostConfigsForUser(userId, includeLegacyUnowned);
        Set<String> result = new LinkedHashSet<>();

        for (DatabaseHostConfig hostConfig : scopedHosts) {
            try {
                if (hostConfig.getDatabases() == null || hostConfig.getDatabases().isBlank()) {
                    continue;
                }

                List<String> dbs = objectMapper.readValue(
                    hostConfig.getDatabases(),
                    new TypeReference<List<String>>() {}
                );
                for (String db : dbs) {
                    if (db != null && !db.isBlank()) {
                        result.add(db.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ 解析主机 {} 的数据库列表失败: {}", hostConfig.getName(), e.getMessage());
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * 校验用户是否可访问指定数据库
     */
    public boolean canAccessDatabase(Integer userId, String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            return false;
        }

        List<String> allowed = getAllowedDatabases(userId);
        return allowed.contains(databaseName.trim());
    }

    /**
     * 断言用户可访问指定数据库
     */
    public void assertCanAccessDatabase(Integer userId, String databaseName) {
        if (!canAccessDatabase(userId, databaseName)) {
            throw new IllegalArgumentException("用户无权访问数据库: " + databaseName);
        }
    }

    /**
     * 断言用户拥有指定主机配置名称
     */
    public void assertOwnsHostConfig(Integer userId, String hostConfigName) {
        validateActiveUser(userId);

        boolean exists = databaseHostConfigRepository.existsByOwnerUserIdAndName(userId, hostConfigName);
        if (!exists) {
            throw new IllegalArgumentException("用户无权访问数据库主机配置: " + hostConfigName);
        }
    }

    /**
     * 根据用户和数据库名解析所属主机配置
     */
    public Optional<DatabaseHostConfig> findOwnedHostConfigByDatabase(Integer userId, String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            return Optional.empty();
        }

        List<DatabaseHostConfig> scopedHosts = getActiveHostConfigsForUser(userId);
        for (DatabaseHostConfig hostConfig : scopedHosts) {
            try {
                if (hostConfig.getDatabases() == null || hostConfig.getDatabases().isBlank()) {
                    continue;
                }

                List<String> dbs = objectMapper.readValue(
                    hostConfig.getDatabases(),
                    new TypeReference<List<String>>() {}
                );

                if (dbs.stream().anyMatch(db -> databaseName.equalsIgnoreCase(db != null ? db.trim() : null))) {
                    return Optional.of(hostConfig);
                }
            } catch (Exception e) {
                log.warn("⚠️ 解析主机 {} 数据库列表失败: {}", hostConfig.getName(), e.getMessage());
            }
        }

        return Optional.empty();
    }

    private void validateActiveUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        if (Boolean.FALSE.equals(user.getStatus())) {
            throw new IllegalArgumentException("用户已被禁用: " + userId);
        }
    }
}

