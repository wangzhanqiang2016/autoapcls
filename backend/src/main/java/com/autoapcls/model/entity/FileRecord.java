package com.autoapcls.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_file_record")
public class FileRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String orgCode;
    private String periodName;
    private Integer stepNo;
    private Long taskId;
    private Long ebsRequestId;
    private LocalDateTime createdAt;
}
