package org.monkey.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.monkey.common.core.dto.Result;
import org.monkey.test.mapper.DictMapper;
import org.monkey.test.model.Dict;
import org.monkey.test.service.DictService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {

    // 插入100万条数据
    @Override
    public void insert100W() {
        log.info("开始插入100万条数据");

        int n = 1;

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            List<Dict> list = new ArrayList<>(1000);
            for (int j = 0; j < 1000; j++) {
                list.add(Dict.builder()
                        .code("code" + n)
                        .name("name" + n)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .createBy("0")
                        .updateBy("0")
                        .delFlag(0)
                        .version(0)
                        .build());
                n++;
            }
            long t1 = System.currentTimeMillis();
            saveBatch(list);
            log.info("第{}批，插入1000条数据完成，耗时：{}毫秒", (i + 1), (System.currentTimeMillis() - t1));
        }
        log.info("插入100万条数据完成, 耗时：{}毫秒", (System.currentTimeMillis() - t0));
    }


    @Override
    public Result<List<Dict>> getAll() {
        log.info("开始查询所有字典数据");
        List<Dict> list = this.lambdaQuery()
                .list();
        log.info("查询所有字典数据完成，共{}条", list.size());
        return Result.success(list);
    }
}
