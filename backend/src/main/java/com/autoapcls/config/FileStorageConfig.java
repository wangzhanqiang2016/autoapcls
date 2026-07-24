package com.autoapcls.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Getter
@Configuration
public class FileStorageConfig {

    @Value("${file.storage.base-path:/data/apclose/files}")
    private String basePath;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Path.of(basePath));
            log.info("文件存储目录已创建: {}", basePath);
        } catch (Exception e) {
            log.warn("无法创建文件存储目录: {}", basePath, e);
        }
    }

    public String getOrgPeriodPath(String orgCode, String periodName) {
        return Path.of(basePath, orgCode, periodName).toString();
    }
}
