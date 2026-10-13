package com.yonerp.po.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    private Long id;

    private String username;

    private String realName;

    private Long deptId;

    private String mobile;

    private String email;

    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}
