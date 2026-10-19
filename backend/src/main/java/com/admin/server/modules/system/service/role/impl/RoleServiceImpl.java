package com.admin.server.modules.system.service.role.impl;

import com.admin.server.common.exception.BusinessException;
import com.admin.server.common.core.PageParam;
import com.admin.server.common.core.PageResult;
import com.admin.server.modules.system.api.role.vo.RoleCreateReqVO;
import com.admin.server.modules.system.api.role.vo.RoleUpdateReqVO;
import com.admin.server.modules.system.dal.dataobject.permission.RoleDO;
import com.admin.server.modules.system.dal.mysql.permission.RoleMapper;
import com.admin.server.modules.system.service.permission.PermissionService;
import com.admin.server.modules.system.service.role.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.admin.server.modules.infra.framework.operlog.OperLogDiffUtils;
import com.admin.server.modules.infra.framework.operlog.OperLogContext;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private RoleMapper roleMapper;
    @Resource
    private PermissionService permissionService;

    @Override
    public List<RoleDO> list(String name, Integer status) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like(RoleDO::getName, name);
        }
        if (status != null) {
            wrapper.eq(RoleDO::getStatus, status);
        }
        List<RoleDO> roles = roleMapper.selectList(wrapper);
        roles.forEach(role -> role.setMenuIds(permissionService.getRoleMenuListByRoleId(role.getId())));
        return roles;
    }

    @Override
    public PageResult<RoleDO> page(PageParam pageParam) {
        Page<RoleDO> page = roleMapper.selectPage(new Page<>(pageParam.getPageNo(), pageParam.getPageSize()), null);
        return PageResult.of(page.getRecords(), page.getTotal());
    }

    @Override
    public RoleDO getById(Long id) {
        return roleMapper.selectById(id);
    }

    @Override
    public Long create(RoleCreateReqVO reqVO) {
        validateRoleCode(reqVO.getCode(), null);
        RoleDO role = new RoleDO();
        role.setName(reqVO.getName());
        role.setCode(reqVO.getCode());
        role.setSort(reqVO.getSort());
        role.setStatus(1);
        role.setRemark(reqVO.getRemark());
        role.setDataScope(normalizeDataScope(reqVO.getDataScope(), reqVO.getDataScopeDeptIds()));
        role.setDataScopeDeptIds(normalizeDeptIds(role.getDataScope(), reqVO.getDataScopeDeptIds()));
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    public void update(RoleUpdateReqVO reqVO) {
        RoleDO role = roleMapper.selectById(reqVO.getId());
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        
        // 计算变更明细并记录操作日志
        List<String> diffItems = OperLogDiffUtils.diff(role, reqVO);
        if (!diffItems.isEmpty()) {
            OperLogContext.setDiffItems(diffItems);
            OperLogContext.setAction("修改角色「" + role.getName() + "」: " + String.join("；", diffItems));
        }

        role.setName(reqVO.getName());
        validateRoleCode(reqVO.getCode(), role.getCode());
        role.setCode(reqVO.getCode());
        role.setSort(reqVO.getSort());
        role.setStatus(reqVO.getStatus());
        role.setRemark(reqVO.getRemark());
        role.setDataScope(normalizeDataScope(reqVO.getDataScope(), reqVO.getDataScopeDeptIds()));
        role.setDataScopeDeptIds(normalizeDeptIds(role.getDataScope(), reqVO.getDataScopeDeptIds()));
        roleMapper.updateById(role);
    }

    @Override
    public void delete(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public PageResult<RoleDO> recyclePage(PageParam pageParam, String name, Integer status) {
        Page<RoleDO> page = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());
        Page<RoleDO> deletedPage = (Page<RoleDO>) roleMapper.selectDeletedPage(page, name, status);
        List<RoleDO> roles = deletedPage.getRecords();
        roles.forEach(role -> role.setMenuIds(permissionService.getRoleMenuListByRoleId(role.getId())));
        return PageResult.of(roles, deletedPage.getTotal());
    }

    @Override
    public void restore(Long id) {
        int rows = roleMapper.restoreById(id);
        if (rows == 0) {
            throw new BusinessException(404, "回收站角色不存在");
        }
    }

    @Override
    public void deletePermanent(Long id) {
        int rows = roleMapper.deletePhysicalById(id);
        if (rows == 0) {
            throw new BusinessException(404, "回收站角色不存在");
        }
    }

    @Override
    public Set<Long> getMenuIdsForAssign(Long roleId) {
        return permissionService.getRoleMenuIdsForAssign(roleId);
    }

    @Override
    public void assignMenu(Long roleId, Set<Long> menuIds) {
        permissionService.assignRoleMenu(roleId, menuIds);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        role.setStatus(status);
        roleMapper.updateById(role);
    }

    /**
     * 系统保留角色编码不允许新建或改名为这些值，避免伪造超级管理员。
     */
    private static final Set<String> RESERVED_ROLE_CODES = Set.of("super_admin", "admin");

    private void validateRoleCode(String code, String existingCode) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (!RESERVED_ROLE_CODES.contains(normalized)) {
            return;
        }
        if (existingCode != null && normalized.equals(existingCode.trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        throw new BusinessException(400, "角色编码「" + code.trim() + "」为系统保留，不允许使用");
    }

    /**
     * 数据范围归一化：未传时默认「全部」，非法值回退「全部」，自定义范围必须选择部门。
     */
    private Integer normalizeDataScope(Integer dataScope, String dataScopeDeptIds) {
        if (dataScope == null) {
            return 1;
        }
        if (dataScope < 1 || dataScope > 5) {
            throw new BusinessException(400, "数据范围取值不合法");
        }
        if (dataScope == 2 && !StringUtils.hasText(dataScopeDeptIds)) {
            throw new BusinessException(400, "自定义数据范围必须选择部门");
        }
        return dataScope;
    }

    /**
     * 仅自定义范围保存部门 ID，其它范围清空，避免切换范围后残留脏数据。
     */
    private String normalizeDeptIds(Integer dataScope, String dataScopeDeptIds) {
        if (dataScope == null || dataScope != 2 || !StringUtils.hasText(dataScopeDeptIds)) {
            return null;
        }
        return dataScopeDeptIds.trim();
    }
}
