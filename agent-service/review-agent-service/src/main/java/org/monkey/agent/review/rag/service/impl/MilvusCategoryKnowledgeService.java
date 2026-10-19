package org.monkey.agent.review.rag.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.milvus.grpc.LoadState;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.GetLoadStateResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.monkey.agent.review.rag.internal.ProfileLoader;
import org.monkey.agent.review.rag.model.CategoryHit;
import org.monkey.agent.review.rag.model.CategoryProfile;
import org.monkey.agent.review.rag.service.CategoryKnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 类目知识库 Milvus 实现
 * <p>
 * 启动流程分 6 步：连接 → 删旧集合 → 建新集合 → 写画像 → 建索引 → 加载进内存
 * 检索流程分 3 步：查询词转向量 → 搜 top-k → 结果包装成 CategoryHit
 */
@Service
@Slf4j
public class MilvusCategoryKnowledgeService implements CategoryKnowledgeService {

    // 集合名 = Milvus 里的"表"名——类目画像全存在这一张表里
    private static final String COLLECTION = "category_profiles";

    // 向量字段名——检索就是在这个字段上按相似度搜
    private static final String FIELD_VECTOR = "vector";

    // 主键字段名——用叶子类目 id 当主键（如 100112 = 手机壳）
    private static final String FIELD_LEAF_ID = "leaf_id";

    // 读画像 JSON 文件的加载器
    @Autowired
    private ProfileLoader profileLoader;

    // bge-m3 embedding 模型（langchain4j-ollama-spring-boot-starter 自动装配）
    // 作用：把文本变成 1024 维向量——"手机壳 防摔 透明" → [0.03, -0.11, ...]（1024 个小数）
    @Autowired
    private OllamaEmbeddingModel embeddingModel;

    // Milvus 客户端——建库/写入/查询全部通过它
    @Autowired
    private MilvusClientV2 client;

    // 画像本地缓存：leafId → 画像（检索命中后按主键回查完整画像，精判要用）
    private final Map<Integer, CategoryProfile> profileMap = new HashMap<>();

    // Gson：Milvus SDK 的行数据用 Gson 的 JsonObject 表示
    private final Gson gson = new Gson();

    /**
     * 启动初始化：连接 → 建库 → 写画像 → flush → 建索引 → load
     */
    @PostConstruct
    public void init() throws Exception {
        // ========== 第 1 步：连接 Milvus ==========

        // ========== 第 2 步：删旧集合 ==========
        // 开发模式每次启动重建：画像 JSON 改动后重启即同步
        // 注意：生产不能每次启动 drop（会清空数据），应改为"画像数据变化时才重建"
        if (client.hasCollection(HasCollectionReq.builder()
                .collectionName(COLLECTION).build())) {
            client.dropCollection(DropCollectionReq.builder()
                    .collectionName(COLLECTION).build());
        }

        // ========== 第 3 步：建集合（先定 schema 字段结构，再建）==========

        // createCollection：按上面定义的 schema 真正创建集合
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(COLLECTION)     // 集合名
                .collectionSchema(getCollectionSchema())       // 上面定义的表结构
                .build());

        // ========== 第 4 步：画像加载 + 批量向量化 + 写入 ==========
        List<CategoryProfile> profiles = profileLoader.load();
        List<JsonObject> rows = new ArrayList<>();   // 待插入的行集合（每行 = 一条画像）
        for (CategoryProfile p : profiles) {
            // 画像侧 embedding 文本：类目名+路径+描述+典型词拼成一段话
            // 商品侧检索时也用同样格式拼查询文本——两侧对称，相似度分数才有可比性
            float[] vector = embeddingModel.embed(p.toEmbeddingText()).content().vector();

            // JsonObject = 一行数据（对应一条画像记录），字段名与 schema 一一对应
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_LEAF_ID, p.getLeafId());            // 主键
            row.add(FIELD_VECTOR, gson.toJsonTree(vector));           // 向量数组 → JSON 数组
            row.addProperty("name", p.getName());
            row.addProperty("path", p.getPath());
            row.addProperty("main_group", p.getMainGroup());
            row.addProperty("description", p.getDescription());
            row.addProperty("typical_words", gson.toJson(p.getTypicalWords()));   // List → JSON 字符串
            row.addProperty("confuse_words", gson.toJson(p.getConfuseWords()));
            row.addProperty("qualification", p.getQualification());
            row.addProperty("required_docs", gson.toJson(p.getRequiredDocs()));
            rows.add(row);

            // 同时写入本地缓存——检索命中后按主键回查完整画像
            profileMap.put(p.getLeafId(), p);
        }
        // insert：全部行一次性写进集合
        client.insert(InsertReq.builder()
                .collectionName(COLLECTION)   // 写进哪个集合
                .data(rows)                   // 要写入的行数据
                .build());

        // ========== 第 5 步：flush → 建索引 → load（顺序固定，Day 2 的坑） ==========
        // flush：把内存缓冲区里的数据持久化成 sealed segment——不 flush 查不到
        client.flush(FlushReq.builder()
                .collectionNames(List.of(COLLECTION)).build());

        // createIndex：给向量字段建索引——没有索引无法检索
        // 2.6 SDK 的建索引参数封装在 IndexParam 里（fieldName + 索引类型 + 相似度度量）
        client.createIndex(CreateIndexReq.builder()
                .collectionName(COLLECTION)
                .indexParams(List.of(IndexParam.builder()
                        .fieldName(FIELD_VECTOR)                              // 给向量字段建索引
                        .indexType(IndexParam.IndexType.AUTOINDEX)            // 索引类型：自动选择
                        .metricType(IndexParam.MetricType.COSINE)             // 相似度度量：余弦
                        .build()))
                .build());

        // loadCollection：把集合加载进内存——检索必须 load 完
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(COLLECTION).build());

        // load 是异步的：调用立即返回，后台慢慢加载——轮询等它完成
        boolean loaded = false;
        for (int i = 0; i < 60; i++) {
            // getLoadStateV2：查询集合当前加载状态
            GetLoadStateResp state = client.getLoadStateV2(GetLoadStateReq.builder()
                    .collectionName(COLLECTION).build());
            if (state.getState() == LoadState.LoadStateLoaded) {   // grpc 枚举常量带前缀：LoadStateLoaded = 已加载完成
                loaded = true;
                break;
            }
            Thread.sleep(1000);   // 没加载完，等 1 秒再查（最多等 60 秒）
        }
        if (!loaded) {
            // 加载超时直接启动失败——知识库不可用，审核服务不该运行
            throw new IllegalStateException("Milvus 集合加载超时: " + COLLECTION);
        }
        log.info("[CategoryKnowledge] Milvus 知识库初始化完成, 画像数={}", profiles.size());
    }

    private static CreateCollectionReq.@NonNull CollectionSchema getCollectionSchema() {

        // createSchema：创建一个空的"表结构"定义对象
        CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();

        // leaf_id：叶子类目 id，同时当主键——画像和 t_category 按它对齐
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_LEAF_ID)     // 字段名
                .dataType(DataType.Int64)     // 字段类型：64 位整数
                .isPrimaryKey(true)           // 是主键——唯一标识一条画像
                .autoID(false)                // 主键不由 Milvus 生成，我们自己填（填 leafId）
                .build());

        // vector：画像向量字段——检索在这个字段上算相似度
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR)             // 字段名
                .dataType(DataType.FloatVector)      // 字段类型：浮点向量（一组小数）
                .dimension(1024)                     // 向量维度 = 1024（bge-m3 输出维度，collection 级硬约束）
                .build());

        // 以下都是标量字段（普通数据，检索命中后可随结果带出）
        schema.addField(AddFieldReq.builder()
                .fieldName("name")               // 类目名："手机壳"
                .dataType(DataType.VarChar)      // 字段类型：变长字符串
                .maxLength(50)                   // VarChar 必须指定最大长度
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("path")               // 类目路径："3C数码 > 手机通讯 > 手机壳"
                .dataType(DataType.VarChar).maxLength(200).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("main_group")         // 主类目群 id（100001 = 3C数码）
                .dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("description")        // 类目描述
                .dataType(DataType.VarChar).maxLength(200).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("typical_words")      // 典型商品词，List 转 JSON 字符串存
                .dataType(DataType.VarChar).maxLength(500).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("confuse_words")      // 易混词，同上
                .dataType(DataType.VarChar).maxLength(500).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("qualification")      // 资质要求："需3C认证" / "无需特殊资质"
                .dataType(DataType.VarChar).maxLength(100).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("required_docs")      // 资质证件清单，List 转 JSON 字符串存
                .dataType(DataType.VarChar).maxLength(200).build());
        return schema;
    }

    /**
     * 检索：商品关键词 → 向量 → Milvus 搜 top-k 最相似的类目画像
     */
    @Override
    public List<CategoryHit> search(List<String> keywords, int topK) {
        log.info("[Node][CategoryKnowledge][search] 检索: keywords={}, topK={}", keywords, topK);
        // 商品侧 embedding 文本：关键词 join（与画像侧 toEmbeddingText 格式对称）
        float[] queryVector = embeddingModel.embed(String.join(" ", keywords)).content().vector();

        // search：在集合里按向量相似度搜索
        SearchResp resp = client.search(SearchReq.builder()
                .collectionName(COLLECTION)                        // 在哪个集合里搜
                .data(List.of(new FloatVec(queryVector)))          // 查询向量（FloatVec 包装 float[]）
                .limit(topK)                                       // 返回最相似的几个结果
                .outputFields(List.of(FIELD_LEAF_ID))              // 结果带出主键（其余字段本地缓存回查）
                .build());
        // 临时排查日志：区分"Milvus 没返回结果"还是"结果转换被 continue 掉"
        log.info("[Node][CategoryKnowledge][search] 原始响应: 查询数={}, 第0个查询命中数={}",
                resp.getSearchResults().size(),
                resp.getSearchResults().isEmpty() ? -1 : resp.getSearchResults().get(0).size());
        log.info("[Node][CategoryKnowledge][search] resp={}", JSON.toJSONString(resp));
        // 结果转换：SearchResult → CategoryHit
        List<CategoryHit> hits = new ArrayList<>();
        // getSearchResults().get(0)：只有一个查询向量，取第 0 个查询的结果列表
        for (SearchResp.SearchResult r : resp.getSearchResults().get(0)) {
            // 命中实体的主键（leaf_id）——用它在本地缓存回查完整画像
            Long leafId = (Long) r.getEntity().get(FIELD_LEAF_ID);
            CategoryProfile profile = profileMap.get(Integer.parseInt(String.valueOf(leafId)));
            if (profile == null) {
                continue;   // 缓存里没有（理论上不会发生），跳过
            }
            hits.add(CategoryHit.builder()
                    .leafId(profile.getLeafId())
                    .name(profile.getName())
                    .path(profile.getPath())
                    .mainGroup(profile.getMainGroup())
                    .description(profile.getDescription())
                    .typicalWords(profile.getTypicalWords())
                    .confuseWords(profile.getConfuseWords())
                    .qualification(profile.getQualification())
                    .score((double) r.getScore())
                    .build());
        }
        return hits;
    }

    @Override
    public CategoryProfile getProfile(Integer leafId) {
        return profileMap.get(leafId);
    }
}
