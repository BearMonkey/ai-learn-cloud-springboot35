package org.monkey.agent.review.service.component.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.monkey.agent.review.service.component.result.CategoryJudgeResult;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "judgeModel")
public interface CategoryJudgeService {

    @SystemMessage(fromResource = "prompts/category-judge/system.txt")
    @UserMessage(fromResource = "prompts/category-judge/user.txt")
    CategoryJudgeResult judge(
            @V("title") String title,                       // @V：IDEA 编译不带 -parameters 时模板占位符靠它取参数名
            @V("description") String description,
            @V("selectedCategoryText") String selectedCategoryText,   // 节点侧把画像对象拼好的文本
            @V("candidateText") String candidateText);               // 节点侧把 top-k 拼好的文本（空命中时传"无"）
}
