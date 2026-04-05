package com.learn.aiintelligenttourism.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AmapToolTest {

	@Test
	void geocodeShouldValidateAddress() {
		AmapTool tool = new AmapTool("");
		String result = tool.geocode("  ", "北京");
		Assertions.assertTrue(result.contains("address 不能为空"));
	}

	@Test
	void shouldSimplifyGeocodeResponse() {
		String raw = """
				{
				  "status":"1",
				  "info":"OK",
				  "count":"1",
				  "geocodes":[
					{
					  "province":"北京市",
					  "city":"北京市",
					  "district":"朝阳区",
					  "location":"116.480881,39.989410"
					}
				  ]
				}
				""";

		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyGeocodeResponse(raw);

		Assertions.assertEquals(
				"{\"location\":\"116.480881,39.989410\",\"province\":\"北京市\",\"city\":\"北京市\",\"district\":\"朝阳区\"}",
				result
		);
	}

	@Test
	void shouldReturnErrorWhenAmapStatusIsFailed() {
		String raw = "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\"}";

		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyGeocodeResponse(raw);

		Assertions.assertTrue(result.contains("高德地理编码返回失败"));
		Assertions.assertTrue(result.contains("INVALID_USER_KEY"));
	}

	@Test
	void distanceMeasureShouldValidateCoordinate() {
		AmapTool tool = new AmapTool("mock-key");
		String result = tool.distanceMeasure("abc", "116.434446,39.90816", "1");
		Assertions.assertTrue(result.contains("origins 格式错误"));
	}

	@Test
	void shouldSimplifyDistanceResponse() {
		String raw = """
				{
				  "status":"1",
				  "info":"OK",
				  "results":[
				    {
				      "origin_id":"1",
				      "dest_id":"1",
				      "distance":"228",
				      "duration":"55"
				    }
				  ]
				}
				""";

		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyDistanceResponse(raw);

		Assertions.assertEquals(
				"{\"status\":\"1\",\"info\":\"OK\",\"result\":{\"origin_id\":\"1\",\"dest_id\":\"1\",\"distance\":\"228\",\"duration\":\"55\"}}",
				result
		);
	}

	@Test
	void shouldReturnErrorWhenDistanceApiFailed() {
		String raw = "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\"}";

		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyDistanceResponse(raw);

		Assertions.assertTrue(result.contains("高德距离测量返回失败"));
		Assertions.assertTrue(result.contains("INVALID_USER_KEY"));
	}

	@Test
	void distanceMeasureShouldValidateType() {
		AmapTool tool = new AmapTool("91bef7f31618d1c1990d33d9e998b4ff");
		String result = tool.distanceMeasure("116.434307,39.90909", "116.434446,39.90816", "2");
		Assertions.assertTrue(result.contains("type 仅支持 0、1、3"));
	}

	@Test
	void distanceMeasureShouldValidateOriginsRequired() {
		// 覆盖 origins 为空分支，避免触发真实网络请求
		AmapTool tool = new AmapTool("mock-key");
		String result = tool.distanceMeasure("   ", "116.434446,39.90816", "1");
		Assertions.assertTrue(result.contains("origins 不能为空"));
	}

	@Test
	void distanceMeasureShouldValidateDestinationRequired() {
		// 覆盖 destination 为空分支
		AmapTool tool = new AmapTool("mock-key");
		String result = tool.distanceMeasure("116.434307,39.90909", "  ", "1");
		Assertions.assertTrue(result.contains("destination 不能为空"));
	}

	@Test
	void distanceMeasureShouldValidateDestinationCoordinate() {
		// 覆盖 destination 坐标格式非法分支
		AmapTool tool = new AmapTool("mock-key");
		String result = tool.distanceMeasure("116.434307,39.90909", "999,999", "1");
		Assertions.assertTrue(result.contains("destination 格式错误"));
	}

	@Test
	void distanceMeasureShouldValidateOriginsCountLimit() {
		// 覆盖 origins 超过 100 个坐标限制分支
		AmapTool tool = new AmapTool("mock-key");
		String tooManyOrigins = buildTooManyOrigins();
		String result = tool.distanceMeasure(tooManyOrigins, "116.434446,39.90816", "1");
		Assertions.assertTrue(result.contains("origins 最多支持 100 个坐标"));
	}

	@Test
	void shouldReturnErrorWhenGeocodeResponseIsInvalidJson() {
		// 覆盖地理编码 JSON 解析异常分支
		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyGeocodeResponse("not-json");
		Assertions.assertTrue(result.contains("解析高德返回失败"));
	}

	@Test
	void shouldReturnErrorWhenDistanceResponseIsInvalidJson() {
		// 覆盖距离测量 JSON 解析异常分支
		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyDistanceResponse("not-json");
		Assertions.assertTrue(result.contains("解析高德距离测量返回失败"));
	}

	@Test
	void shouldReturnErrorWhenDistanceResultsIsEmpty() {
		// 覆盖 status=1 但 results 为空的业务兜底分支
		String raw = """
				{
				  "status":"1",
				  "info":"OK",
				  "results":[]
				}
				""";

		AmapTool tool = new AmapTool("mock-key");
		String result = tool.simplifyDistanceResponse(raw);
		Assertions.assertTrue(result.contains("未找到可用的距离结果"));
	}

	private String buildTooManyOrigins() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 101; i++) {
			if (i > 0) {
				builder.append("|");
			}
			builder.append("116.434307,39.90909");
		}
		return builder.toString();
	}
}

