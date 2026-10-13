package com.yonerp.po.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色
 */
@Data
@TableName("sys_role")
public class SysRole {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
