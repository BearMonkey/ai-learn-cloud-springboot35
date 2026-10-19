package org.monkey.agent.review.rag.service;

import org.monkey.agent.review.rag.model.CategoryHit;
import org.monkey.agent.review.rag.model.CategoryProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface CategoryKnowledgeService {

    CategoryProfile getProfile(Integer leafId);

    /**
     * 检索与关键词最相似的 topK 个类目画像
     * 将来拆服务后，这个接口换 Feign 实现，调用方（CategoryCheckNode）一行不改
     */
    List<CategoryHit> search(List<String> keywords, int topK);
}
