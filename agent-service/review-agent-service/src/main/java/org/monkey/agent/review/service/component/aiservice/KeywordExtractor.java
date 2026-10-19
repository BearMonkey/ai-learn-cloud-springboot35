package org.monkey.agent.review.service.component.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.monkey.agent.review.service.component.model.keywords;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "parseInputModel")
public interface KeywordExtractor {
    @SystemMessage(fromResource = "prompts/keyword-extractor/system.txt")
    @UserMessage(fromResource = "prompts/keyword-extractor/user.txt")
    keywords extract(@V("title") String title, @V("description") String description);
}
