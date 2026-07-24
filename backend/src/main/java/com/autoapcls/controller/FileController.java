package com.autoapcls.controller;

import com.autoapcls.common.Result;
import com.autoapcls.model.entity.FileRecord;
import com.autoapcls.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // 下载单个文件
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) throws IOException {
        FileRecord record = fileStorageService.getFileRecord(id);
        byte[] content = fileStorageService.readFile(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", record.getFileName());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
