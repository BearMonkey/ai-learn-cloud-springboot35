package org.monkey.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.monkey.common.core.dto.Result;
import org.monkey.test.model.Dict;

import java.util.List;

public interface DictService extends IService<Dict> {
    void insert100W();

    Result<List<Dict>> getAll();
}
