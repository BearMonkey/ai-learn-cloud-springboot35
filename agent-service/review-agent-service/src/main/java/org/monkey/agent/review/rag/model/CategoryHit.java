package org.monkey.agent.review.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 检索命中结果——一条候选类目画像 + 相似度分数
 * 字段镜像 CategoryProfile：第三层 LLM 精判要把画像内容喂进上下文，
 * 检索结果带全字段，精判就不用二次查画像
 */
@Data
@Builder
public class CategoryHit {

    // 叶子类目 id（如 100112 = 手机壳）——与 t_category 对齐
    private Integer leafId;

    // 类目名："手机壳"
    private String name;

    // 类目路径："3C数码 > 手机通讯 > 手机壳"
    private String path;

    // 主类目群 id（100001 = 3C数码）
    private Integer mainGroup;

    // 类目描述
    private String description;

    // 典型商品词
    private List<String> typicalWords;

    // 易混词（兄弟类目名）
    private List<String> confuseWords;

    // 资质要求："需3C认证" / "无需特殊资质"
    private String qualification;

    // 相似度分数（0~1，越高越像）
    private Double score;
}
