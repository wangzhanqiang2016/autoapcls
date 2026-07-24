package com.autoapcls.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ap_close_task")
public class ApCloseTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer stepNo;
    private String periodName;
    private Integer orgId;
    private Integer ouId;
    private Integer ledgerId;
    private String status;
    private Long ebsRequestId;
    private String ebsRequestStatus;
    private String outputFilePath;
    private String errorMessage;
    private String paramsJson;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
