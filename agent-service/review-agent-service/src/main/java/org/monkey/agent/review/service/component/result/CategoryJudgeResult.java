package org.monkey.agent.review.service.component.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor   // AiServices 结构化输出要求无参构造器（Jackson 反序列化用）
@AllArgsConstructor
public class CategoryJudgeResult {
    private String verdict;              // 三态结论字符串：PASS/REJECT/ESCALATE（不用枚举——LLM 可能输出小写 pass，节点侧归一化兜底）
    private String suggestedCategory;    // verdict=REJECT 时 LLM 认为商品该属于的类目名
    private String reason;               // 判断理由：给人工复核台看的原文
    private double confidence;           // 置信度 0~1：第四层兜底用（PASS 但 <0.9 → 转人工）
}