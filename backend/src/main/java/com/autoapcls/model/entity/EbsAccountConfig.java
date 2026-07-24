package com.autoapcls.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_ebs_account_config")
public class EbsAccountConfig {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String respType;
    private String ebsUserName;
    private String ebsPassword;
    private String description;
}
