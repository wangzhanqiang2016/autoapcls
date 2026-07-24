package com.autoapcls.config;

import com.autoapcls.mapper.UserEbsMappingMapper;
import com.autoapcls.model.entity.UserEbsMapping;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首次启动时，为没有密码的存量用户设置默认密码 "123456"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordInitializer {

    private final UserEbsMappingMapper userEbsMappingMapper;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaultPasswords() {
        List<UserEbsMapping> users = userEbsMappingMapper.selectList(
                new LambdaQueryWrapper<UserEbsMapping>()
                        .isNull(UserEbsMapping::getPassword)
                        .or()
                        .eq(UserEbsMapping::getPassword, "")
        );
        if (users.isEmpty()) {
            log.info("[密码初始化] 所有用户已有密码，跳过");
            return;
        }
        String defaultHash = passwordEncoder.encode("123456");
        for (UserEbsMapping user : users) {
            user.setPassword(defaultHash);
            userEbsMappingMapper.updateById(user);
        }
        log.info("[密码初始化] 已为 {} 个用户设置默认密码 (123456)", users.size());
    }
}
