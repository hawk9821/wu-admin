package com.admin.server.modules.system.api.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterReqVO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 12, message = "用户名长度 4~12 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;
    @NotBlank(message = "密码不能为空")
    // 密码可能是前端 SM2 加密后的密文（长度远超明文），此处只做非空校验；
    // 明文长度 6~50 的约束由 AuthServiceImpl#validatePasswordComplexity 在解密后校验。
    private String password;
    private String nickname;
    private String uuid;
    private String code;
}
