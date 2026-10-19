package org.monkey.agent.review.service.component.aiservice;

import cn.hutool.dfa.WordTree;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本规则仓库：加载违禁品词库 / 极限词库 / 排除上下文 / 敏感信息正则
 * 词库文件在 resources/text-rules/ 下，每行一条，空行和 # 开头的注释行忽略
 */
@Component
@Slf4j
public class TextRuleRepository {

    // 违禁品词文件：强证据词，命中无条件驳回
    private static final String BANNED_PATH = "/text-rules/banned-words.txt";
    // 极限词文件：广告法极限词，命中驳回（排除上下文内跳过）
    private static final String LIMIT_PATH = "/text-rules/limit-words.txt";
    // 排除上下文文件：极限词的合法搭配（如"第一章"），匹配前先挖掉
    private static final String EXCLUSION_PATH = "/text-rules/limit-exclusions.txt";
    // 敏感信息正则文件：每行一条正则（手机号/微信/QQ/URL）
    private static final String SENSITIVE_PATH = "/text-rules/sensitive-patterns.txt";

    // 违禁品词列表
    private List<String> bannedWords = List.of();
    // 极限词列表
    private List<String> limitWords = List.of();
    // 排除上下文列表
    private List<String> limitExclusions = List.of();
    // 敏感信息正则列表（编译好的 Pattern）
    private List<Pattern> sensitivePatterns = List.of();
    // 违禁品 DFA 匹配树
    private WordTree bannedTree;
    // 极限词 DFA 匹配树
    private WordTree limitTree;

    /**
     * 启动加载全部词库：文件缺失抛异常（fail-fast），内容为空允许（词库运营起步期）
     */
    @PostConstruct
    public void init() {
        bannedWords = loadLines(BANNED_PATH);
        limitWords = loadLines(LIMIT_PATH);
        limitExclusions = loadLines(EXCLUSION_PATH);
        sensitivePatterns = loadPatterns(SENSITIVE_PATH);
        // WordTree：DFA 多模匹配树——一段文本一次遍历命中词库所有词，复杂度 O(文本长度)
        bannedTree = new WordTree();
        bannedTree.addWords(bannedWords);
        limitTree = new WordTree();
        limitTree.addWords(limitWords);
        log.info("[TextRuleRepository] 词库加载完成: 违禁品={}, 极限词={}, 排除={}, 正则={}",
                bannedWords.size(), limitWords.size(), limitExclusions.size(), sensitivePatterns.size());
    }

    /**
     * 读词库文件：每行一个词，去掉首尾空白，忽略空行和 # 注释行
     */
    private List<String> loadLines(String path) {
        List<String> lines = new ArrayList<>();
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("词库文件不存在: " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        reader.lines().forEach(line -> {
            String word = line.trim();
            if (!word.isEmpty() && !word.startsWith("#")) {
                lines.add(word);
            }
        });
        return lines;
    }

    /**
     * 读正则文件：每行一条正则，编译失败抛异常（fail-fast，坏正则不该静默跳过）
     */
    private List<Pattern> loadPatterns(String path) {
        List<Pattern> patterns = new ArrayList<>();
        for (String line : loadLines(path)) {
            patterns.add(Pattern.compile(line));
        }
        return patterns;
    }

    // ========== 访问器 ==========

    public List<String> bannedWords() {
        return bannedWords;
    }

    public List<String> limitWords() {
        return limitWords;
    }

    public List<String> limitExclusions() {
        return limitExclusions;
    }

    public List<Pattern> sensitivePatterns() {
        return sensitivePatterns;
    }

    public WordTree bannedTree() {
        return bannedTree;
    }

    public WordTree limitTree() {
        return limitTree;
    }
}
