package com.admin.server.modules.ticket.dal.mysql.approval;

import com.admin.server.modules.ticket.dal.dataobject.approval.ApprovalFormDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface ApprovalFormMapper extends BaseMapper<ApprovalFormDO> {
    @Select({
            "<script>",
            "SELECT * FROM sys_approval_form",
            "WHERE deleted = 1",
            "<if test='title != null and title != \"\"'>",
            "  AND title LIKE CONCAT('%', #{title}, '%')",
            "</if>",
            "<if test='formType != null and formType != \"\"'>",
            "  AND form_type = #{formType}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "  AND status = #{status}",
            "</if>",
            "<if test='deptIds != null'>",
            "  <choose>",
            "    <when test='deptIds.size() > 0'>",
            "      AND (applicant_user_id IN (SELECT id FROM sys_user WHERE deleted = 0 AND dept_id IN",
            "            <foreach collection='deptIds' item='visibleDeptId' open='(' separator=',' close=')'>#{visibleDeptId}</foreach>)",
            "        OR approver_user_id IN (SELECT id FROM sys_user WHERE deleted = 0 AND dept_id IN",
            "            <foreach collection='deptIds' item='visibleDeptId' open='(' separator=',' close=')'>#{visibleDeptId}</foreach>))",
            "    </when>",
            "    <otherwise>AND 1 = 0</otherwise>",
            "  </choose>",
            "</if>",
            "<if test='selfOnly and currentUserId != null'>",
            "  AND (applicant_user_id = #{currentUserId} OR approver_user_id = #{currentUserId})",
            "</if>",
            "ORDER BY update_time DESC",
            "</script>"
    })
    IPage<ApprovalFormDO> selectDeletedPage(Page<ApprovalFormDO> page,
                                            @Param("title") String title,
                                            @Param("formType") String formType,
                                            @Param("status") String status,
                                            @Param("deptIds") java.util.List<Long> deptIds,
                                            @Param("selfOnly") boolean selfOnly,
                                            @Param("currentUserId") Long currentUserId);

    @Select("SELECT * FROM sys_approval_form WHERE id = #{id} AND deleted = 1")
    ApprovalFormDO selectDeletedById(@Param("id") Long id);

    @Update("UPDATE sys_approval_form SET deleted = 0, update_time = NOW() WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM sys_approval_form WHERE id = #{id} AND deleted = 1")
    int deletePhysicalById(@Param("id") Long id);
}
