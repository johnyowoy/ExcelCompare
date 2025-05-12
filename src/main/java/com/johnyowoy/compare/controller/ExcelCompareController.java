package com.johnyowoy.compare.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ExcelCompareController {

    private static final Logger logger = LoggerFactory.getLogger(ExcelCompareController.class);

    @Autowired
    private Map<String, Map<String, Object>> uploadedFiles;

    @PostMapping("/compare")
    public Map<String, Object> compareFiles(@RequestBody Map<String, Object> request) {
        logger.info("收到比較請求：{}", request);
        String mainFileId = (String) request.get("mainFileId");
        @SuppressWarnings("unchecked")
        List<String> otherFileIds = (List<String>) request.get("otherFileIds");
        String keyField = (String) request.get("keyField");
        @SuppressWarnings("unchecked")
        List<String> selectedFields = (List<String>) request.get("selectedFields");

        // 驗證輸入
        if (mainFileId == null || otherFileIds == null || otherFileIds.isEmpty()) {
            logger.warn("必須選擇主要檔案和至少一個次要檔案：mainFileId={}, otherFileIds={}", mainFileId, otherFileIds);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇主要檔案和至少一個次要檔案");
        }
        if (keyField == null || keyField.trim().isEmpty()) {
            logger.warn("無效比較鍵：{}", keyField);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇比較鍵");
        }
        if (selectedFields == null || selectedFields.isEmpty()) {
            logger.warn("未選擇比較欄位");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇至少一個比較欄位");
        }

        // 獲取主要檔案數據
        Map<String, Object> mainFileData = uploadedFiles.get(mainFileId);
        if (mainFileData == null) {
            logger.warn("主要檔案不存在，fileId：{}", mainFileId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "主要檔案不存在");
        }
        String mainFilename = (String) mainFileData.get("filename");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> mainData = (List<Map<String, String>>) mainFileData.get("data");

        // 驗證主要檔案包含 keyField
        if (!mainData.isEmpty() && !mainData.get(0).containsKey(keyField)) {
            logger.warn("主要檔案缺少比較鍵：{}，filename：{}", keyField, mainFilename);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主要檔案缺少 " + keyField + " 欄位");
        }

        // 獲取次要檔案數據
        List<Map<String, Object>> otherFilesData = new ArrayList<>();
        for (String fileId : otherFileIds) {
            Map<String, Object> fileData = uploadedFiles.get(fileId);
            if (fileData == null) {
                logger.warn("次要檔案不存在，fileId：{}", fileId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "次要檔案不存在");
            }
            String filename = (String) fileData.get("filename");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> data = (List<Map<String, String>>) fileData.get("data");
            if (!data.isEmpty() && !data.get(0).containsKey(keyField)) {
                logger.warn("次要檔案缺少比較鍵：{}，filename：{}", keyField, filename);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "次要檔案 " + filename + " 缺少 " + keyField + " 欄位");
            }
            otherFilesData.add(fileData);
        }

        // 準備結果
        Map<String, Object> response = new HashMap<>();
        List<String> headers = new ArrayList<>();
        headers.add(keyField);
        headers.addAll(selectedFields);
        headers.add("status");
        headers.add("source_file");

        List<Map<String, String>> resultData = new ArrayList<>();

        // 比較邏輯：以主要檔案為基準
        for (Map<String, String> mainRow : mainData) {
            String keyValue = mainRow.get(keyField);
            Map<String, String> resultRow = new HashMap<>();
            resultRow.put(keyField, keyValue);
            resultRow.put("source_file", mainFilename);
            boolean matched = false;

            for (Map<String, Object> otherFile : otherFilesData) {
                String otherFilename = (String) otherFile.get("filename");
                @SuppressWarnings("unchecked")
                List<Map<String, String>> otherData = (List<Map<String, String>>) otherFile.get("data");

                for (Map<String, String> otherRow : otherData) {
                    if (otherRow.get(keyField).equals(keyValue)) {
                        matched = true;
                        boolean isDifferent = false;
                        for (String field : selectedFields) {
                            String mainValue = mainRow.get(field) != null ? mainRow.get(field) : "";
                            String otherValue = otherRow.get(field) != null ? otherRow.get(field) : "";
                            resultRow.put(field, mainValue + (otherValue.equals(mainValue) ? "" : " | " + otherValue));
                            if (!mainValue.equals(otherValue)) {
                                isDifferent = true;
                            }
                        }
                        resultRow.put("status", isDifferent ? "different" : "match");
                        break;
                    }
                }
                if (matched) break;
            }

            if (!matched) {
                for (String field : selectedFields) {
                    resultRow.put(field, mainRow.get(field) != null ? mainRow.get(field) : "");
                }
                resultRow.put("status", "missing");
            }

            resultData.add(resultRow);
        }

        // 檢查次要檔案中獨有的鍵值
        for (Map<String, Object> otherFile : otherFilesData) {
            String otherFilename = (String) otherFile.get("filename");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> otherData = (List<Map<String, String>>) otherFile.get("data");

            for (Map<String, String> otherRow : otherData) {
                String keyValue = otherRow.get(keyField);
                boolean existsInMain = mainData.stream().anyMatch(row -> row.get(keyField).equals(keyValue));
                if (!existsInMain) {
                    Map<String, String> resultRow = new HashMap<>();
                    resultRow.put(keyField, keyValue);
                    resultRow.put("source_file", otherFilename);
                    for (String field : selectedFields) {
                        resultRow.put(field, otherRow.get(field) != null ? otherRow.get(field) : "");
                    }
                    resultRow.put("status", "missing");
                    resultData.add(resultRow);
                }
            }
        }

        response.put("headers", headers);
        response.put("data", resultData);
        logger.info("比較完成，結果行數：{}", resultData.size());
        return response;
    }

    @PostMapping("/compare/details")
    public Map<String, Object> getDetails(@RequestBody Map<String, Object> request) {
        logger.info("收到明細表請求：{}", request);
        String mainFileId = (String) request.get("mainFileId");
        @SuppressWarnings("unchecked")
        List<String> otherFileIds = (List<String>) request.get("otherFileIds");
        String keyField = (String) request.get("keyField");
        @SuppressWarnings("unchecked")
        List<String> selectedFields = (List<String>) request.get("selectedFields");

        // 驗證輸入
        if (mainFileId == null || otherFileIds == null || otherFileIds.isEmpty()) {
            logger.warn("必須選擇主要檔案和至少一個次要檔案：mainFileId={}, otherFileIds={}", mainFileId, otherFileIds);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇主要檔案和至少一個次要檔案");
        }
        if (keyField == null || keyField.trim().isEmpty()) {
            logger.warn("無效比較鍵：{}", keyField);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇比較鍵");
        }
        if (selectedFields == null || selectedFields.isEmpty()) {
            logger.warn("未選擇比較欄位");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇至少一個比較欄位");
        }

        // 獲取所有檔案數據
        Map<String, Object> mainFileData = uploadedFiles.get(mainFileId);
        if (mainFileData == null) {
            logger.warn("主要檔案不存在，fileId：{}", mainFileId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "主要檔案不存在");
        }
        String mainFilename = (String) mainFileData.get("filename");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> mainData = (List<Map<String, String>>) mainFileData.get("data");

        List<Map<String, Object>> otherFilesData = new ArrayList<>();
        for (String fileId : otherFileIds) {
            Map<String, Object> fileData = uploadedFiles.get(fileId);
            if (fileData == null) {
                logger.warn("次要檔案不存在，fileId：{}", fileId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "次要檔案不存在");
            }
            otherFilesData.add(fileData);
        }

        // 準備結果
        Map<String, Object> response = new HashMap<>();
        List<String> headers = new ArrayList<>();
        headers.add(keyField);
        headers.addAll(selectedFields);
        headers.add("source_file");

        List<Map<String, String>> resultData = new ArrayList<>();

        // 合併主要檔案數據
        for (Map<String, String> mainRow : mainData) {
            String keyValue = mainRow.get(keyField);
            Map<String, String> resultRow = new HashMap<>();
            resultRow.put(keyField, keyValue);
            resultRow.put("source_file", mainFilename);
            for (String field : selectedFields) {
                resultRow.put(field, mainRow.get(field) != null ? mainRow.get(field) : "");
            }
            resultData.add(resultRow);
        }

        // 合併次要檔案數據
        for (Map<String, Object> otherFile : otherFilesData) {
            String otherFilename = (String) otherFile.get("filename");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> otherData = (List<Map<String, String>>) otherFile.get("data");

            for (Map<String, String> otherRow : otherData) {
                String keyValue = otherRow.get(keyField);
                Map<String, String> resultRow = new HashMap<>();
                resultRow.put(keyField, keyValue);
                resultRow.put("source_file", otherFilename);
                for (String field : selectedFields) {
                    resultRow.put(field, otherRow.get(field) != null ? otherRow.get(field) : "");
                }
                resultData.add(resultRow);
            }
        }

        response.put("headers", headers);
        response.put("data", resultData);
        logger.info("明細表完成，結果行數：{}", resultData.size());
        return response;
    }

    @PostMapping("/compare/stats")
    public Map<String, Object> getStats(@RequestBody Map<String, Object> request) {
        logger.info("收到統計表請求：{}", request);
        String mainFileId = (String) request.get("mainFileId");
        @SuppressWarnings("unchecked")
        List<String> otherFileIds = (List<String>) request.get("otherFileIds");
        String keyField = (String) request.get("keyField");
        @SuppressWarnings("unchecked")
        List<String> selectedFields = (List<String>) request.get("selectedFields");

        // 驗證輸入
        if (mainFileId == null || otherFileIds == null || otherFileIds.isEmpty()) {
            logger.warn("必須選擇主要檔案和至少一個次要檔案：mainFileId={}, otherFileIds={}", mainFileId, otherFileIds);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇主要檔案和至少一個次要檔案");
        }
        if (keyField == null || keyField.trim().isEmpty()) {
            logger.warn("無效比較鍵：{}", keyField);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇比較鍵");
        }

        // 獲取所有檔案數據
        Map<String, Object> mainFileData = uploadedFiles.get(mainFileId);
        if (mainFileData == null) {
            logger.warn("主要檔案不存在，fileId：{}", mainFileId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "主要檔案不存在");
        }
        String mainFilename = (String) mainFileData.get("filename");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> mainData = (List<Map<String, String>>) mainFileData.get("data");

        List<Map<String, Object>> otherFilesData = new ArrayList<>();
        for (String fileId : otherFileIds) {
            Map<String, Object> fileData = uploadedFiles.get(fileId);
            if (fileData == null) {
                logger.warn("次要檔案不存在，fileId：{}", fileId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "次要檔案不存在");
            }
            otherFilesData.add(fileData);
        }

        // 統計數據：按 keyField 彙總
        Map<String, Map<String, Object>> statsMap = new HashMap<>();
        List<String> headers = new ArrayList<>();
        headers.add(keyField);
        headers.add("appearance_count");
        headers.add("difference_count");
        headers.add("files_present");

        // 統計主要檔案
        for (Map<String, String> mainRow : mainData) {
            String keyValue = mainRow.get(keyField);
            Map<String, Object> statEntry = statsMap.computeIfAbsent(keyValue, k -> new HashMap<>());
            statEntry.put("keyValue", keyValue);
            statEntry.put("appearance_count", ((int) statEntry.getOrDefault("appearance_count", 0)) + 1);
            @SuppressWarnings("unchecked")
            Set<String> filesPresent = (Set<String>) statEntry.computeIfAbsent("files_present", k -> new HashSet<String>());
            filesPresent.add(mainFilename);
            statEntry.put("difference_count", 0); // 初始差異計數
        }

        // 統計次要檔案並計算差異
        for (Map<String, Object> otherFile : otherFilesData) {
            String otherFilename = (String) otherFile.get("filename");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> otherData = (List<Map<String, String>>) otherFile.get("data");

            for (Map<String, String> otherRow : otherData) {
                String keyValue = otherRow.get(keyField);
                Map<String, Object> statEntry = statsMap.computeIfAbsent(keyValue, k -> new HashMap<>());
                statEntry.put("keyValue", keyValue);
                statEntry.put("appearance_count", ((int) statEntry.getOrDefault("appearance_count", 0)) + 1);
                @SuppressWarnings("unchecked")
                Set<String> filesPresent = (Set<String>) statEntry.computeIfAbsent("files_present", k -> new HashSet<String>());
                filesPresent.add(otherFilename);

                // 檢查與主要檔案的差異
                boolean hasDifference = false;
                for (Map<String, String> mainRow : mainData) {
                    if (mainRow.get(keyField).equals(keyValue)) {
                        for (String field : selectedFields) {
                            String mainValue = mainRow.get(field) != null ? mainRow.get(field) : "";
                            String otherValue = otherRow.get(field) != null ? otherRow.get(field) : "";
                            if (!mainValue.equals(otherValue)) {
                                hasDifference = true;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (hasDifference) {
                    statEntry.put("difference_count", ((int) statEntry.getOrDefault("difference_count", 0)) + 1);
                }
            }
        }

        // 轉換為結果數據
        List<Map<String, String>> resultData = new ArrayList<>();
        for (Map<String, Object> stat : statsMap.values()) {
            Map<String, String> row = new HashMap<>();
            row.put(keyField, (String) stat.get("keyValue"));
            row.put("appearance_count", String.valueOf(stat.get("appearance_count")));
            row.put("difference_count", String.valueOf(stat.get("difference_count")));
            @SuppressWarnings("unchecked")
            Set<String> filesPresent = (Set<String>) stat.get("files_present");
            row.put("files_present", String.join(", ", filesPresent));
            resultData.add(row);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("headers", headers);
        response.put("data", resultData);
        logger.info("統計表完成，結果行數：{}", resultData.size());
        return response;
    }
}