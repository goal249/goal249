package com.tea.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tea.trace.entity.TeaAddress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AddressMapper extends BaseMapper<TeaAddress> {
}
