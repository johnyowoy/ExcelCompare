package com.johnyowoy.compare.controller;

import com.johnyowoy.compare.model.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExcelUploadController {

    private static final Logger logger = LoggerFactory.getLogger(ExcelUploadController.class);
    private final Map<String, Map<String, Object>> uploadedFiles = new HashMap<>();

    @Value("${app.excel-data-dir:target/ExcelData}")
    private String excelDataDir;

    @Autowired
    private ExcelReader excelReader;

    @PostMapping("/upload")
    public Map<String, Object> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        logger.info("收到上傳請求，文件數量：{}", files != null ? files.length : 0);
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> uploaded = new ArrayList<>();

        // 解析並驗證儲存路徑
        Path excelDataPath = Paths.get(excelDataDir).toAbsolutePath().normalize();
        try {
            if (!Files.exists(excelDataPath)) {
                Files.createDirectories(excelDataPath);
                logger.info("創建 ExcelData 目錄：{}", excelDataPath);
            }
            if (!Files.isWritable(excelDataPath)) {
                logger.error("ExcelData 目錄不可寫：{}", excelDataPath);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "儲存目錄不可寫");
            }
        } catch (IOException e) {
            logger.error("無法創建 ExcelData 目錄：{}，錯誤：{}", excelDataPath, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "無法創建儲存目錄");
        }

        if (files == null || files.length == 0) {
            logger.warn("沒有收到任何檔案");
            response.put("uploaded", uploaded);
            return response;
        }

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                logger.warn("檔案名稱為空，跳過");
                continue;
            }
            if (!filename.toLowerCase().endsWith(".xlsx")) {
                logger.warn("無效檔案格式：{}", filename);
                continue;
            }
            if (file.isEmpty()) {
                logger.warn("檔案為空：{}", filename);
                continue;
            }

            try {
                // 先解析檔案
                Map<String, Object> fileData = excelReader.readExcel(file);

                // 儲存檔案
                Path filePath = excelDataPath.resolve(filename);
                file.transferTo(filePath.toFile());
                logger.info("檔案儲存至：{}", filePath);

                String fileId = UUID.randomUUID().toString();
                fileData.put("filename", filename);
                uploadedFiles.put(fileId, fileData);

                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("fileId", fileId);
                fileInfo.put("filename", filename);
                uploaded.add(fileInfo);

                logger.info("成功處理檔案：{}，fileId：{}", filename, fileId);
            } catch (IOException e) {
                logger.error("處理檔案失敗：{}，錯誤：{}", filename, e.getMessage(), e);
                continue;
            } catch (Exception e) {
                logger.error("解析檔案失敗：{}，錯誤：{}", filename, e.getMessage(), e);
                continue;
            }
        }

        response.put("uploaded", uploaded);
        logger.info("上傳完成，上傳檔案數：{}", uploaded.size());
        if (uploaded.isEmpty() && files.length > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "沒有檔案成功上傳，請檢查檔案格式或伺服器日誌");
        }
        return response;
    }

    @GetMapping("/files")
    public Map<String, Object> getUploadedFiles() {
        logger.info("收到檔案清單請求");
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> files = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : uploadedFiles.entrySet()) {
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("fileId", entry.getKey());
            fileInfo.put("filename", (String) entry.getValue().get("filename"));
            files.add(fileInfo);
        }

        response.put("files", files);
        logger.info("返回檔案清單，數量：{}", files.size());
        return response;
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        logger.info("收到下載請求，fileId：{}", fileId);
        Map<String, Object> fileData = uploadedFiles.get(fileId);
        if (fileData == null) {
            logger.warn("檔案不存在，fileId：{}", fileId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "檔案不存在");
        }

        String filename = (String) fileData.get("filename");
        Path filePath = Paths.get(excelDataDir).toAbsolutePath().normalize().resolve(filename);
        try {
            if (!Files.exists(filePath)) {
                logger.warn("檔案不存在於磁碟：{}", filePath);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "檔案不存在於伺服器");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("檔案不可讀：{}", filePath);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "無法讀取檔案");
            }

            logger.info("準備下載檔案：{}", filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            logger.error("無效檔案路徑：{}，錯誤：{}", filePath, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "無效檔案路徑");
        }
    }

    @GetMapping("/file/{fileId}/content")
    public Map<String, Object> getFileContent(@PathVariable String fileId) {
        logger.info("收到檔案內容請求，fileId：{}", fileId);
        Map<String, Object> fileData = uploadedFiles.get(fileId);
        if (fileData == null) {
            logger.warn("檔案不存在，fileId：{}", fileId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "檔案不存在");
        }

        String filename = (String) fileData.get("filename");
        Path filePath = Paths.get(excelDataDir).toAbsolutePath().normalize().resolve(filename);
        try {
            if (!Files.exists(filePath)) {
                logger.warn("檔案不存在於磁碟：{}", filePath);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "檔案不存在於伺服器");
            }

            // 重用 ExcelReader 解析檔案
            Map<String, Object> content = new HashMap<>();
            List<String> headers = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, String>> data = (List<Map<String, String>>) fileData.get("data");

            // 從第一行數據提取表頭
            if (!data.isEmpty()) {
                headers.addAll(data.get(0).keySet());
            }

            content.put("headers", headers);
            content.put("data", data);
            logger.info("返回檔案內容，filename：{}", filename);
            return content;
        } catch (Exception e) {
            logger.error("無法解析檔案內容：{}，錯誤：{}", filename, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "無法解析檔案內容");
        }
    }

    @DeleteMapping("/file/{fileId}")
    public Map<String, Object> deleteFile(@PathVariable String fileId) {
        logger.info("收到刪除檔案請求，fileId：{}", fileId);
        Map<String, Object> fileData = uploadedFiles.get(fileId);
        if (fileData == null) {
            logger.warn("檔案不存在，fileId：{}", fileId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "檔案不存在");
        }

        String filename = (String) fileData.get("filename");
        Path filePath = Paths.get(excelDataDir).toAbsolutePath().normalize().resolve(filename);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("已刪除檔案：{}", filePath);
            } else {
                logger.warn("檔案不存在於磁碟：{}", filePath);
            }

            uploadedFiles.remove(fileId);
            logger.info("已移除檔案記錄，fileId：{}", fileId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "檔案已移除");
            return response;
        } catch (IOException e) {
            logger.error("無法刪除檔案：{}，錯誤：{}", filePath, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "無法刪除檔案");
        }
    }
}