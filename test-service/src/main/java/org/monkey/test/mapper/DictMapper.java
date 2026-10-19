package org.monkey.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.test.model.Dict;

@Mapper
public interface DictMapper extends BaseMapper<Dict> {
}
