package com.johnyowoy.compare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompareApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void contextLoads() {
		// 驗證應用上下文載入
	}

	@Test
	void testIndexPage() {
		// Arrange: 無需設置，直接訪問靜態檔案
		// Act: 請求 index.html
		ResponseEntity<String> response = restTemplate.getForEntity("/index.html", String.class);
		// Assert: 檢查狀態碼和內容
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("Excel 比較工具"));
	}

}
