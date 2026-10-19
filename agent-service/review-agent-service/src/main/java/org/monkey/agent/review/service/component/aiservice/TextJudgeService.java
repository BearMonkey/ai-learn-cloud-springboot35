package org.monkey.agent.review.service.component.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.monkey.agent.review.service.component.result.TextJudgeResult;

/**
 * EXPLICIT + judgeModel：与 CategoryJudgeService 同一套 wiring 模式，换模型只改 LlmConfig
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "judgeModel")
public interface TextJudgeService {

    @SystemMessage(fromResource = "prompts/text-judge/system.txt")
    @UserMessage(fromResource = "prompts/text-judge/user.txt")
    TextJudgeResult judge(
            @V("title") String title,                       // @V：IDEA 编译不带 -parameters 时模板占位符靠它取参数名
            @V("description") String description,
            @V("bannedWordsRef") String bannedWordsRef);    // 违禁词库参考文本：LLM 对照找变体
}
