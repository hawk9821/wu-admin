package com.admin.server.modules.system.api.role.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleUpdateReqVO {
    @NotNull(message = "角色ID不能为空")
    private Long id;
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 30, message = "角色名称最多 30 个字符")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码最多 50 个字符")
    private String code;
    private Integer sort;
    private Integer status;
    private String remark;
    /** 数据范围 1:全部 2:自定义 3:本部门 4:本部门及以下 5:仅本人 */
    private Integer dataScope;
    /** 自定义数据范围部门 ID，逗号分隔 */
    private String dataScopeDeptIds;
}
