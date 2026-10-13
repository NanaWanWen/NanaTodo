package com.yonerp.po.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonerp.po.base.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
