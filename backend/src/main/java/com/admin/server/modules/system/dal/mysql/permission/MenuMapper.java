package com.admin.server.modules.system.dal.mysql.permission;

import com.admin.server.modules.system.dal.dataobject.permission.MenuDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<MenuDO> {

    /**
     * 一次性取回用户全部有效权限码（用户角色 → 角色菜单 → 菜单，单次 JOIN）。
     * <p>替代「先查菜单 ID 再回查菜单」的两段式实现：后者每次权限校验都要全表扫描
     * {@code sys_menu}，接口越多放大越明显。</p>
     */
    @Select("""
            SELECT DISTINCT m.permission
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            INNER JOIN sys_role_menu rm ON rm.role_id = r.id
            INNER JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
            WHERE ur.user_id = #{userId}
              AND m.permission IS NOT NULL
              AND m.permission <> ''
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 一次性取回用户可见菜单（含目录与页面），用于侧栏渲染。
     */
    @Select("""
            SELECT DISTINCT m.id, m.name, m.permission, m.type, m.sort, m.parent_id,
                   m.path, m.icon, m.status, m.component,
                   m.create_time, m.update_time, m.creator, m.updater, m.deleted
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
            INNER JOIN sys_role r ON r.id = rm.role_id AND r.deleted = 0
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND m.deleted = 0
            ORDER BY m.sort ASC, m.id ASC
            """)
    List<MenuDO> selectMenusByUserId(@Param("userId") Long userId);
    @Select({
            "<script>",
            "SELECT * FROM sys_menu",
            "WHERE deleted = 1",
            "<if test='name != null and name != \"\"'>",
            "  AND name LIKE CONCAT('%', #{name}, '%')",
            "</if>",
            "<if test='status != null'>",
            "  AND status = #{status}",
            "</if>",
            "ORDER BY update_time DESC",
            "</script>"
    })
    IPage<MenuDO> selectDeletedPage(Page<MenuDO> page,
                                    @Param("name") String name,
                                    @Param("status") Integer status);

    @Update("UPDATE sys_menu SET deleted = 0, update_time = NOW() WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM sys_menu WHERE id = #{id} AND deleted = 1")
    int deletePhysicalById(@Param("id") Long id);
}
