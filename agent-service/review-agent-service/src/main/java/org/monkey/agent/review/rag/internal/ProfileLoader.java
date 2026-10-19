package org.monkey.agent.review.rag.internal;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.rag.model.CategoryProfile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 画像数据加载器——读取 resources/category/category-profiles.json
 * 将来画像数据源换成 DB / Nacos 时，只改这一个类
 */
@Component
@Slf4j
public class ProfileLoader {

    // 画像文件在 classpath 下的位置（打包进 jar，启动时读一次）
    private static final String PROFILE_PATH = "/category/category-profiles.json";

    /**
     * 加载全部类目画像
     */
    public List<CategoryProfile> load() {
        try (InputStream in = getClass().getResourceAsStream(PROFILE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("类目画像文件不存在: " + PROFILE_PATH);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<CategoryProfile> profiles = JSON.parseArray(text, CategoryProfile.class);
            if (profiles == null || profiles.isEmpty()) {
                throw new IllegalStateException("类目画像文件为空: " + PROFILE_PATH);
            }
            log.info("[ProfileLoader] 画像加载完成, 数量={}", profiles.size());
            return profiles;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // 画像数据是审核的必需依赖——加载失败直接启动失败（fail-fast），
            // 不能吞掉异常让服务带着残缺知识库静默运行
            throw new IllegalStateException("类目画像加载失败", e);
        }
    }
}
