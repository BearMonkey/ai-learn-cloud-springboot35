package org.monkey.agent.review.rag.model;

import lombok.Data;

import java.util.List;

/**
 * 类目画像——类目知识库的一条"文档"
 * 对应 resources/category/category-profiles.json 中的一条记录
 */
@Data
public class CategoryProfile {

    // 叶子类目 id（如 100112 = 手机壳），与 t_category 表的 category_id 对齐
    private Integer leafId;

    // 类目名："手机壳"
    private String name;

    // 类目路径："3C数码 > 手机通讯 > 手机壳"
    private String path;

    // 主类目群 id（100001 = 3C数码）——第一层规则判断用
    private Integer mainGroup;

    // 类目描述：这个类目卖什么
    private String description;

    // 典型商品词：商品标题出现这些词基本属于该类目
    private List<String> typicalWords;

    // 易混词：兄弟类目名，检索打分时的负信号
    private List<String> confuseWords;

    // 资质要求："需3C认证" / "无需特殊资质"
    private String qualification;

    // 需要的资质证件清单
    private List<String> requiredDocs;

    /**
     * 构造画像侧的 embedding 文本
     * 格式：类目名 + 路径 + 描述 + 典型词拼接
     * 商品侧检索时也用同样的拼接格式——两侧格式对称，相似度分数才有可比性
     */
    public String toEmbeddingText() {
        return name + " " + path + " " + description + " " + String.join(" ", typicalWords);
    }

    public String toLlmJudgeText() {
        return "类目名称：" + name + System.lineSeparator() +
                "类目路径：" + path + System.lineSeparator() +
                "类目描述：" + description + System.lineSeparator() +
                "典型商品词：" + String.join(" ", typicalWords) + System.lineSeparator() +
                "易混词：" + String.join(" ", confuseWords) + System.lineSeparator() +
                "资质要求：" + qualification + System.lineSeparator() +
                "需要的资质证件：" + String.join(" ", requiredDocs) + System.lineSeparator();
    }
}
