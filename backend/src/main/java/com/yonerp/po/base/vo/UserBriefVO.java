package com.yonerp.po.base.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户简要信息（登录下拉与用户列表）
 */
@Data
public class UserBriefVO {

    private Long id;

    private String username;

    private String realName;

    private Long deptId;

    private Integer status;

    private List<String> roles;
}
