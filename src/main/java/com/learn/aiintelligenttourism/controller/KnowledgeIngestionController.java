package com.learn.aiintelligenttourism.controller;

import com.learn.aiintelligenttourism.RAG.KnowledgeIngestionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeIngestionController {

	@Resource
	private KnowledgeIngestionService knowledgeIngestionService;

	/**
	 * 手动触发文档入库：适合新增 markdown 后手动执行一次。
	 */
	@PostMapping("/ingest")
	public Map<String, Object> ingest() {
		return knowledgeIngestionService.ingestNow();
	}
}

