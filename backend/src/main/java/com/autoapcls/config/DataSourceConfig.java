package com.autoapcls.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置
 * - 主数据源 (H2)：业务数据，@Primary，Flyway 管理
 * - EBS 数据源 (Oracle)：只读查询，不参与 Flyway 迁移
 */
@Configuration
public class DataSourceConfig {

    // ═══════════ 主数据源 H2 (Flyway 管理) ═══════════

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    // ═══════════ EBS Oracle 数据源 (Flyway 不管) ═══════════

    @Bean("ebsDataSource")
    @ConfigurationProperties(prefix = "ebs.datasource")
    public DataSource ebsDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("ebsNamedJdbcTemplate")
    public NamedParameterJdbcTemplate ebsNamedJdbcTemplate(
            @Qualifier("ebsDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
