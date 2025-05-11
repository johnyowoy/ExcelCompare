package com.johnyowoy.compare.model;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class ExcelReader {

    public Map<String, Object> readExcel(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IOException("Excel 檔案無有效工作表");
            }

            List<Map<String, String>> data = new ArrayList<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Excel 檔案無表頭");
            }

            // 讀取表頭
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell != null ? cell.getStringCellValue() : "");
            }

            // 讀取資料
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    rowData.put(headers.get(j), cell != null ? cell.toString() : "");
                }
                data.add(rowData);
            }

            result.put("data", data);
        } catch (IOException e) {
            throw new IOException("無法解析 Excel 檔案: " + e.getMessage(), e);
        }
        return result;
    }
}