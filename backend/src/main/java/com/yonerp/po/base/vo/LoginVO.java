package com.yonerp.po.base.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录结果（简化鉴权：选择用户即登录）
 */
@Data
public class LoginVO {

    private Long userId;

    private String username;

    private String realName;

    /** 用户拥有的全部角色 */
    private List<String> roles;

    /** 当前生效角色 */
    private String currentRole;

    private LocalDateTime loginTime;
}
