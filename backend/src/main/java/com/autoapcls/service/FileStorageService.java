package com.autoapcls.service;

import com.autoapcls.config.FileStorageConfig;
import com.autoapcls.mapper.FileRecordMapper;
import com.autoapcls.model.entity.FileRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageConfig fileStorageConfig;
    private final FileRecordMapper fileRecordMapper;

    // 保存文件到本地存储
    public FileRecord saveFile(String orgCode, String periodName, Integer stepNo,
                                Long taskId, Long ebsRequestId, String fileName,
                                byte[] content, String fileType) throws IOException {
        Path dir = Path.of(fileStorageConfig.getOrgPeriodPath(orgCode, periodName));
        Files.createDirectories(dir);
        Path filePath = dir.resolve(fileName);
        Files.write(filePath, content);

        FileRecord record = new FileRecord();
        record.setFileName(fileName);
        record.setFilePath(filePath.toString());
        record.setFileSize((long) content.length);
        record.setFileType(fileType);
        record.setOrgCode(orgCode);
        record.setPeriodName(periodName);
        record.setStepNo(stepNo);
        record.setTaskId(taskId);
        record.setEbsRequestId(ebsRequestId);
        record.setCreatedAt(LocalDateTime.now());
        fileRecordMapper.insert(record);

        log.info("文件已保存: {} ({})", fileName, record.getFilePath());
        return record;
    }

    // 读取文件内容
    public byte[] readFile(Long fileId) throws IOException {
        FileRecord record = fileRecordMapper.selectById(fileId);
        if (record == null) throw new IllegalArgumentException("文件不存在: " + fileId);
        return Files.readAllBytes(Path.of(record.getFilePath()));
    }

    // 获取文件元数据
    public FileRecord getFileRecord(Long fileId) {
        return fileRecordMapper.selectById(fileId);
    }
}
