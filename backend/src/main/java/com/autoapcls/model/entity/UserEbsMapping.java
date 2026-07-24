package com.autoapcls.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_ebs_mapping")
public class UserEbsMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String feishuOpenId;
    private String feishuName;
    private String ebsUserName;
    private Integer ebsUserId;
    private String password;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
