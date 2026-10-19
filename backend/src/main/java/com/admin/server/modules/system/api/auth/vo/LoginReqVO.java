package com.admin.server.modules.system.api.auth.vo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginReqVO {
    /** account | sms | email */
    @Size(max = 16, message = "登录类型过长")
    private String loginType;
    @Size(max = 64, message = "账号长度不能超过 64 个字符")
    private String username;
    /** 可能是 SM2 密文，长度上限按密文预留 */
    @Size(max = 512, message = "密码长度不能超过 512 个字符")
    private String password;
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;
    @Size(max = 16, message = "邮箱验证码长度为 16 个字符以内")
    private String emailCode;
    @Size(max = 64, message = "验证码标识过长")
    private String uuid;
    @Size(max = 16, message = "验证码长度为 16 个字符以内")
    private String code;
    /** 客户端内存随机 ID，用于绑定本次会话签名与加密密钥 */
    @Size(max = 128, message = "客户端标识长度不能超过 128 个字符")
    private String clientId;
}
