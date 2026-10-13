package com.yonerp.po.base.vo;

import lombok.Data;

/**
 * 角色信息
 */
@Data
public class RoleVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private Integer sort;
}
