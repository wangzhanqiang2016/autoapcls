package com.autoapcls.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_session")
public class UserSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String feishuOpenId;
    private Integer selectedRespId;
    private String selectedRespName;
    private Integer selectedOrgId;
    private String selectedOrgCode;
    private Integer defaultOuId;
    private String defaultOuName;
    private Integer defaultLedgerId;
    private String defaultLedgerName;
    private String periodName;
    private LocalDateTime loginAt;
    private LocalDateTime expireAt;
}
