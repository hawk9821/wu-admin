package com.admin.server.modules.infra.dal.mysql.file;

import com.admin.server.modules.infra.dal.dataobject.file.SysFileDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysFileMapper extends BaseMapper<SysFileDO> {

    @Select({
            "<script>",
            "SELECT * FROM sys_file",
            "WHERE deleted = 1",
            "<if test='originalName != null and originalName != \"\"'>",
            "  AND original_name LIKE CONCAT('%', #{originalName}, '%')",
            "</if>",
            "<if test='deptIds != null'>",
            "  <choose>",
            "    <when test='deptIds.size() > 0'>",
            "      AND dept_id IN",
            "      <foreach collection='deptIds' item='deptId' open='(' separator=',' close=')'>#{deptId}</foreach>",
            "    </when>",
            "    <otherwise>AND 1 = 0</otherwise>",
            "  </choose>",
            "</if>",
            "<if test='createBy != null'>AND create_by = #{createBy}</if>",
            "ORDER BY update_time DESC",
            "</script>"
    })
    IPage<SysFileDO> selectDeletedPage(Page<SysFileDO> page,
                                       @Param("originalName") String originalName,
                                       @Param("deptIds") List<Long> deptIds,
                                       @Param("createBy") String createBy);

    @Select("SELECT * FROM sys_file WHERE id = #{id} AND deleted = 1")
    SysFileDO selectDeletedById(@Param("id") Long id);

    @Update("UPDATE sys_file SET deleted = 0, update_time = NOW() WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM sys_file WHERE id = #{id} AND deleted = 1")
    int deletePhysicalById(@Param("id") Long id);

    @Select("SELECT id FROM sys_file WHERE deleted = 1 AND update_time < #{cutoff} LIMIT 500")
    List<Long> selectExpiredRecycleIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 按 objectKey 反查文件记录，刻意不过滤 deleted，便于守卫区分「已逻辑删除」和「从未入库」。
     * <p>指定 bucket 时优先精确匹配；不指定时返回任意一条（兼容旧路由没有 bucket 的场景）。</p>
     */
    @Select({
            "<script>",
            "SELECT id, dept_id AS deptId, bucket_name AS bucketName, create_by AS createBy, deleted",
            "FROM sys_file",
            "WHERE file_path = #{objectName}",
            "<if test='bucketName != null and bucketName != \"\"'>",
            "  AND bucket_name = #{bucketName}",
            "</if>",
            "ORDER BY deleted ASC, id DESC",
            "LIMIT 1",
            "</script>"
    })
    SysFileDO selectByFilePathIncludingDeleted(@Param("bucketName") String bucketName,
                                               @Param("objectName") String objectName);

    /**
     * 分组侧栏批量计数：一次查询替代“每个分组查一次”，并复用文件列表的数据范围与分类规则。
     */
    @Select({
            "<script>",
            "SELECT group_id AS groupId, COUNT(*) AS total FROM sys_file",
            "WHERE deleted = 0 AND group_id IN",
            "<foreach collection='groupIds' item='gid' open='(' separator=',' close=')'>#{gid}</foreach>",
            "<if test='deptIds != null'>",
            "  <choose>",
            "    <when test='deptIds.size() > 0'>",
            "      AND dept_id IN",
            "      <foreach collection='deptIds' item='visibleDeptId' open='(' separator=',' close=')'>#{visibleDeptId}</foreach>",
            "    </when>",
            "    <otherwise>AND 1 = 0</otherwise>",
            "  </choose>",
            "</if>",
            "<if test='selfCreateBy != null'>AND create_by = #{selfCreateBy}</if>",
            "<if test='fileTypePrefix != null'>AND file_type LIKE CONCAT(#{fileTypePrefix}, '%')</if>",
            "<if test='otherCategory'>",
            "  AND file_type NOT LIKE 'image/%'",
            "  AND file_type NOT LIKE 'video/%'",
            "  AND file_type NOT LIKE 'audio/%'",
            "</if>",
            "AND file_path NOT LIKE 'chat/%'",
            "AND file_path NOT LIKE 'images/chat/%'",
            "AND file_path NOT LIKE 'files/chat/%'",
            "GROUP BY group_id",
            "</script>"
    })
    List<Map<String, Object>> countGroupedFiles(@Param("groupIds") List<Long> groupIds,
                                                @Param("deptIds") List<Long> deptIds,
                                                @Param("selfCreateBy") String selfCreateBy,
                                                @Param("fileTypePrefix") String fileTypePrefix,
                                                @Param("otherCategory") boolean otherCategory);
}
