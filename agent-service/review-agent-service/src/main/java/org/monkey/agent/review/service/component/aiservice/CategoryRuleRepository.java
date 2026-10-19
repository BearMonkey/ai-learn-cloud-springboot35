package org.monkey.agent.review.service.component.aiservice;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.service.component.model.MainGroup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class CategoryRuleRepository {

    // 启动后填充，运行时只读——词表量小，全量常驻内存
    private List<MainGroup> groups = List.of();

    @PostConstruct
    public void load() {
        // 读 resources/category/main-group-keywords.json
        String json;
        try (InputStream in = getClass().getResourceAsStream("/category/main-group-keywords.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("类目群词表加载失败", e);
        }
        this.groups = JSON.parseArray(json, MainGroup.class);
        log.info("[CategoryRuleRepository] 加载群词表完成, 群数量={}", groups.size());
    }

    public List<MainGroup> groups() {
        return groups;
    }
}
