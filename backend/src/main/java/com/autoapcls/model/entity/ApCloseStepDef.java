package com.autoapcls.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("ap_close_step_def")
public class ApCloseStepDef {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer stepNo;
    private String stepName;
    private String stepType;
    private String ebsProgram;
    private String ebsRespType;
    private String description;
    private Integer sortOrder;
}
