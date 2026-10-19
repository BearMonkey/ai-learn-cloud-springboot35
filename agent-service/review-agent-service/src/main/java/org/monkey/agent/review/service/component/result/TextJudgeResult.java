package org.monkey.agent.review.service.component.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文本审核 LLM 精判结果
 */
@Data
@NoArgsConstructor   // AiServices 结构化输出要求无参构造器（Jackson 反序列化用）
@AllArgsConstructor
public class TextJudgeResult {
    private String verdict;            // 三态结论字符串：PASS/REJECT/ESCALATE（LLM 可能输出小写，节点侧归一化）
    private List<String> riskPhrases;  // 发现的违禁词变体列表（"zui低"、"蕞低"）
    private String reason;             // 判断理由：给人工复核台看的原文
    private double confidence;         // 置信度 0~1：兜底层裁决用
}
