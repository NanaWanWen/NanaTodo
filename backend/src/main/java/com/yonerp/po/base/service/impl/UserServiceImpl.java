package com.yonerp.po.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yonerp.po.base.entity.SysRole;
import com.yonerp.po.base.entity.SysUser;
import com.yonerp.po.base.entity.SysUserRole;
import com.yonerp.po.base.mapper.SysRoleMapper;
import com.yonerp.po.base.mapper.SysUserMapper;
import com.yonerp.po.base.mapper.SysUserRoleMapper;
import com.yonerp.po.base.service.UserService;
import com.yonerp.po.base.vo.LoginVO;
import com.yonerp.po.base.vo.RoleVO;
import com.yonerp.po.base.vo.UserBriefVO;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.security.SessionStore;
import com.yonerp.po.security.UserLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户与角色服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserLoader {

    private static final TypeReference<List<UserBriefVO>> USER_LIST_TYPE = new TypeReference<>() {
    };

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SessionStore sessionStore;

    private final CacheService cacheService;

    @Override
    public CurrentUser loadCurrentUser(Long userId) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getDeleted, 0));
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw BizException.forbiddenRole();
        }
        List<RoleCode> roles = loadRoles(userId);
        if (roles.isEmpty()) {
            throw BizException.param("用户未分配角色，请联系管理员");
        }
        return CurrentUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .deptId(user.getDeptId())
                .roles(roles)
                .currentRole(roles.get(0))
                .build();
    }

    @Override
    public LoginVO login(Long userId) {
        CurrentUser user = loadCurrentUser(userId);
        sessionStore.save(user);
        return toLoginVO(user);
    }

    @Override
    public LoginVO switchRole(Long userId, String roleCode) {
        RoleCode role = RoleCode.of(roleCode);
        CurrentUser user = loadCurrentUser(userId);
        if (!user.hasRole(role)) {
            throw BizException.forbiddenRole();
        }
        user.setCurrentRole(role);
        sessionStore.save(user);
        log.info("切换角色：userId={}, role={}", userId, role.getCode());
        return toLoginVO(user);
    }

    @Override
    public void logout(Long userId) {
        sessionStore.remove(userId);
    }

    @Override
    public List<UserBriefVO> listUsers() {
        List<UserBriefVO> cached = cacheService.get(RedisKeys.cacheUserList(), USER_LIST_TYPE).orElse(null);
        if (cached != null) {
            return cached;
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .orderByAsc(SysUser::getId));
        List<UserBriefVO> result = new ArrayList<>();
        for (SysUser user : users) {
            UserBriefVO vo = new UserBriefVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setDeptId(user.getDeptId());
            vo.setStatus(user.getStatus());
            vo.setRoles(loadRoles(user.getId()).stream().map(RoleCode::getCode).toList());
            result.add(vo);
        }
        cacheService.set(RedisKeys.cacheUserList(), result, Duration.ofMinutes(30), 300);
        return result;
    }

    @Override
    public List<RoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .orderByAsc(SysRole::getSort));
        List<RoleVO> result = new ArrayList<>();
        for (SysRole role : roles) {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleName(role.getRoleName());
            vo.setDescription(role.getDescription());
            vo.setSort(role.getSort());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (loadCurrentUser(userId) == null) {
            throw BizException.notFound("用户不存在");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole relation = new SysUserRole();
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                userRoleMapper.insert(relation);
            }
        }
        // 角色变更：清理会话与用户列表缓存
        sessionStore.remove(userId);
        cacheService.delete(RedisKeys.cacheUserList());
        log.info("分配角色：userId={}, roleIds={}", userId, roleIds);
    }

    /**
     * 加载用户角色列表
     */
    private List<RoleCode> loadRoles(Long userId) {
        List<SysUserRole> relations = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).toList();
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getDeleted, 0));
        List<RoleCode> result = new ArrayList<>();
        for (SysRole role : roles) {
            result.add(RoleCode.of(role.getRoleCode()));
        }
        return result;
    }

    private LoginVO toLoginVO(CurrentUser user) {
        LoginVO vo = new LoginVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoles(user.getRoles().stream().map(RoleCode::getCode).toList());
        vo.setCurrentRole(user.getCurrentRole() == null ? null : user.getCurrentRole().getCode());
        vo.setLoginTime(LocalDateTime.now());
        return vo;
    }
}
