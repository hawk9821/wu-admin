package com.admin.server.modules.system.service.permission;

import com.admin.server.modules.system.dal.dataobject.dept.DeptDO;
import com.admin.server.modules.system.dal.dataobject.user.UserDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.List;

/**
 * 组织（部门）数据权限
 * <p>按当前登录用户所属角色配置的数据范围，计算其可见部门集合，并在业务列表查询中显式拼接过滤条件。
 * 超级管理员不受限制；没有登录上下文的后台任务默认“无数据范围”，
 * 避免异步线程误拿全量数据。</p>
 */
public interface DataScopeService {

    /**
     * 当前登录用户的数据范围
     */
    DataScopeContext getCurrentScope();

    /**
     * 当前登录用户的部门 ID（可能为空）
     */
    Long getCurrentDeptId();

    /**
     * 用户列表按组织范围过滤
     */
    void applyUserScope(LambdaQueryWrapper<UserDO> wrapper);

    /**
     * 部门列表按组织范围过滤
     */
    void applyDeptScope(LambdaQueryWrapper<DeptDO> wrapper);

    /**
     * 按记录上的部门字段过滤（例如文件、文件分组）
     */
    <T> void applyDeptIdScope(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> deptIdColumn);

    /**
     * 使用已计算好的数据范围过滤，避免同一次请求重复查询用户/角色/部门。
     */
    <T> void applyDeptIdScope(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> deptIdColumn,
                              DataScopeContext context);

    /**
     * 按“创建人用户 ID”过滤（例如工单、审批单），通过用户表映射到可见部门
     */
    <T> void applyCreatorScope(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> creatorUserIdColumn);

    /**
     * 可见部门 ID 列表
     */
    List<Long> getVisibleDeptIds();

    /**
     * 当前用户是否可见指定部门（用于按 ID 的写操作与详情校验）
     */
    boolean isDeptVisible(Long deptId);

    /**
     * 当前用户是否可见指定用户（用于按 ID 的写操作与详情校验）
     */
    boolean isUserVisible(Long userId);

    /**
     * 可见用户的子查询 SQL；全部数据范围时返回 null。
     * <p>仅由后端根据数值型部门 ID 拼接，不接受外部输入。</p>
     */
    String visibleUserIdsSql();

    /**
     * 当前数据范围快照
     */
    record DataScopeContext(boolean all, boolean selfOnly, List<Long> deptIds, Long userId, Long deptId) {

        public boolean denied() {
            return !all && !selfOnly && deptIds.isEmpty();
        }
    }
}
