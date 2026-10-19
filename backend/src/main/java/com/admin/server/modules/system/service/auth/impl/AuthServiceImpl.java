package com.admin.server.modules.system.service.auth.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.admin.server.common.exception.BusinessException;
import com.admin.server.common.util.IpLocationUtils;
import com.admin.server.common.util.UserAgentUtils;
import com.admin.server.framework.security.core.service.TokenService;
import com.admin.server.framework.security.core.util.Sm2Util;
import com.admin.server.modules.system.enums.ErrorCodeConstants;
import com.admin.server.modules.system.api.auth.vo.EmailCodeReqVO;
import com.admin.server.modules.system.api.auth.vo.LoginReqVO;
import com.admin.server.modules.system.api.auth.vo.RegisterReqVO;
import com.admin.server.modules.system.api.auth.vo.SmsCodeReqVO;
import com.admin.server.modules.system.api.auth.vo.SmsCodeVerifyReqVO;
import com.admin.server.modules.system.dal.dataobject.loginlog.LoginLogDO;
import com.admin.server.modules.system.dal.dataobject.permission.RoleDO;
import com.admin.server.modules.system.dal.dataobject.user.UserDO;
import com.admin.server.modules.system.dal.mysql.permission.RoleMapper;
import com.admin.server.modules.system.dal.mysql.user.UserMapper;
import com.admin.server.modules.ticket.service.approval.RegisterApprovalService;
import com.admin.server.modules.system.service.auth.AuthService;
import com.admin.server.modules.system.service.auth.LoginLockService;
import com.admin.server.modules.system.service.auth.SliderCaptchaService;
import com.admin.server.modules.system.service.config.SystemConfigHelper;
import com.admin.server.modules.trade.service.email.EmailCodeService;
import com.admin.server.modules.system.service.loginlog.LoginLogService;
import com.admin.server.modules.infra.service.monitor.OnlineUserService;
import com.admin.server.modules.system.service.permission.PermissionService;
import com.admin.server.modules.trade.framework.sms.AliyunDypnsSmsVerifyService;
import com.admin.server.modules.trade.framework.sms.SmsServiceFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource private UserMapper userMapper;
    @Resource private PasswordEncoder passwordEncoder;
    @Resource private TokenService tokenService;
    @Resource private PermissionService permissionService;
    @Resource private RedissonClient redissonClient;
    @Resource private RoleMapper roleMapper;
    @Resource private LoginLogService loginLogService;
    @Resource private OnlineUserService onlineUserService;
    @Resource private SystemConfigHelper systemConfigHelper;
    @Resource private RegisterApprovalService registerApprovalService;
    @Resource private SmsServiceFactory smsServiceFactory;
    @Resource private AliyunDypnsSmsVerifyService aliyunDypnsSmsVerifyService;
    @Resource private LoginLockService loginLockService;
    @Resource private SliderCaptchaService sliderCaptchaService;
    @Resource private EmailCodeService emailCodeService;
    @Resource private com.admin.server.framework.security.core.service.VerificationCodeGuard verificationCodeGuard;
    @Resource private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final String CAPTCHA_KEY = "captcha:";
    private static final String SMS_CODE_KEY = "sms:login:";
    private static final String SMS_LIMIT_KEY = "sms:limit:";
    private static final String SMS_DAILY_PHONE_KEY = "sms:daily:phone:";
    private static final String SMS_DAILY_IP_KEY = "sms:daily:ip:";
    /** BCrypt 哈希格式：$2a$/$2b$/$2y$ + 成本因子 + 53 位密文 */
    private static final Pattern BCRYPT_HASH_PATTERN =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./0-9A-Za-z]{53}$");

    @Override
    public Map<String, Object> generateCaptcha(String scene, String clientIp) {
        boolean captchaEnabled = "register".equalsIgnoreCase(scene)
                ? systemConfigHelper.isRegisterCaptchaEnabled()
                : systemConfigHelper.isCaptchaEnabled();
        if (!captchaEnabled) {
            throw new BusinessException(400, "当前未启用验证码");
        }
        String captchaType = "register".equalsIgnoreCase(scene)
                ? systemConfigHelper.getRegisterCaptchaType()
                : systemConfigHelper.getCaptchaType();
        if (SystemConfigHelper.CAPTCHA_TYPE_SLIDER.equals(captchaType)) {
            throw new BusinessException(400, "当前为滑块验证码，无需拉取图片验证码");
        }
        String rlMsg = rateLimitByIp(clientIp, "captcha", systemConfigHelper.getCaptchaPerIpMinute());
        if (rlMsg != null) {
            throw new BusinessException(429, rlMsg);
        }
        String uuid = IdUtil.simpleUUID();
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 50);
        String code = lineCaptcha.getCode();
        String imageBase64 = lineCaptcha.getImageBase64();
        RBucket<String> bucket = redissonClient.getBucket(CAPTCHA_KEY + uuid);
        setWithTtl(bucket, code.toLowerCase(), 5, TimeUnit.MINUTES);
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", uuid);
        result.put("img", imageBase64);
        return result;
    }

    @Override
    public Map<String, Object> createSliderChallenge(String scene, String clientIp) {
        return sliderCaptchaService.createChallenge(scene, clientIp);
    }

    @Override
    public Map<String, Object> getPublicConfig() {
        return systemConfigHelper.buildPublicConfig();
    }

    @Override
    public Map<String, Object> loginByAccount(LoginReqVO reqVO, String clientIp, String userAgent) {
        String username = reqVO.getUsername() == null ? "" : reqVO.getUsername().trim();
        String rlMsg = rateLimitByIp(clientIp, "login", systemConfigHelper.getLoginPerIpMinute());
        if (rlMsg != null) {
            recordLoginLog(null, username, 1, rlMsg, clientIp, userAgent);
            throw new BusinessException(429, rlMsg);
        }
        String lockMessage = loginLockService.checkLoginLockMessage(username, clientIp);
        if (lockMessage != null) {
            recordLoginLog(null, username, 1, lockMessage, clientIp, userAgent);
            throw new BusinessException(429, lockMessage);
        }
        String captchaErr = validateLoginCaptcha(reqVO, clientIp);
        if (captchaErr != null) {
            // 验证码错误不计入密码失败次数，避免攻击者用空验证码锁死任意账号
            recordLoginLog(null, username, 1, captchaErr, clientIp, userAgent);
            throw new BusinessException(400, captchaErr);
        }
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null) {
            loginLockService.recordLoginFailure(username, clientIp);
            recordLoginLog(null, username, 1, "用户不存在", clientIp, userAgent);
            throw new BusinessException(400, "账号或密码错误");
        }
        String plainPassword = resolvePassword(reqVO.getPassword());
        String storedPassword = user.getPassword();
        // 数据库密码必须是 BCrypt 哈希：非哈希值（明文/MD5/加密串）会让 Spring 只打印
        // "Encoded password does not look like BCrypt" 这种难以定位的警告，这里提前给出明确诊断
        if (storedPassword == null || !BCRYPT_HASH_PATTERN.matcher(storedPassword).matches()) {
            log.error("账号 [{}] 的密码字段不是合法的 BCrypt 哈希（长度={}），请通过管理后台重置密码或执行密码重置 SQL",
                    username, storedPassword == null ? 0 : storedPassword.length());
            loginLockService.recordLoginFailure(username, clientIp);
            recordLoginLog(user.getId(), username, 1, "密码格式非法，需要重置", clientIp, userAgent);
            throw new BusinessException(400, "账号或密码错误");
        }
        if (!passwordEncoder.matches(plainPassword, storedPassword)) {
            loginLockService.recordLoginFailure(username, clientIp);
            recordLoginLog(user.getId(), username, 1, "密码错误", clientIp, userAgent);
            throw new BusinessException(400, "账号或密码错误");
        }
        String statusErr = checkUserLoginStatus(user);
        if (statusErr != null) {
            recordLoginLog(user.getId(), username, 1, statusErr, clientIp, userAgent);
            throw new BusinessException(403, statusErr);
        }
        return completeLogin(user, clientIp, userAgent, reqVO.getClientId());
    }

    @Override
    public Map<String, Object> loginBySms(LoginReqVO reqVO, String clientIp, String userAgent) {
        if (!systemConfigHelper.isSmsLoginEnabled()) {
            throw new BusinessException(400, "当前未启用短信登录");
        }
        if (!systemConfigHelper.isSmsEnabled()) {
            throw new BusinessException(400, "短信功能未启用");
        }
        String phone = reqVO.getPhone() == null ? "" : reqVO.getPhone().trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(400, "请输入正确的手机号");
        }
        UserDO user = findUserByMobile(phone);
        String username = user != null ? user.getUsername() : phone;
        String rlMsg = rateLimitByIp(clientIp, "login", systemConfigHelper.getLoginPerIpMinute());
        if (rlMsg != null) {
            recordLoginLog(null, username, 1, rlMsg, clientIp, userAgent);
            throw new BusinessException(429, rlMsg);
        }
        String lockMessage = loginLockService.checkLoginLockMessage(username, clientIp);
        if (lockMessage != null) {
            recordLoginLog(null, username, 1, lockMessage, clientIp, userAgent);
            throw new BusinessException(429, lockMessage);
        }
        String captchaErr = validateSmsLoginCaptcha(reqVO);
        if (captchaErr != null) {
            // 短信验证码错误由 VerificationCodeGuard 单独计数，不再累加账号密码失败次数
            recordLoginLog(null, username, 1, captchaErr, clientIp, userAgent);
            throw new BusinessException(400, captchaErr);
        }
        if (user == null) {
            loginLockService.recordLoginFailure(username, clientIp);
            recordLoginLog(null, username, 1, "该手机号未绑定任何账号", clientIp, userAgent);
            throw new BusinessException(400, "该手机号未绑定任何账号");
        }
        String statusErr = checkUserLoginStatus(user);
        if (statusErr != null) {
            recordLoginLog(user.getId(), username, 1, statusErr, clientIp, userAgent);
            throw new BusinessException(403, statusErr);
        }
        return completeLogin(user, clientIp, userAgent, reqVO.getClientId());
    }

    @Override
    public void sendSmsCode(SmsCodeReqVO reqVO, String clientIp) {
        if (!systemConfigHelper.isSmsEnabled()) {
            throw new BusinessException(400, "短信功能未启用");
        }
        if (!systemConfigHelper.isSmsLoginEnabled()) {
            throw new BusinessException(400, "当前未启用短信验证码登录");
        }
        String phone = reqVO.getPhone() == null ? "" : reqVO.getPhone().trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(400, "请输入正确的手机号");
        }
        String rateErr = checkSmsSendRateLimit(phone, clientIp);
        if (rateErr != null) {
            throw new BusinessException(429, rateErr);
        }
        if (findUserByMobile(phone) == null) {
            throw new BusinessException(400, "该手机号未绑定任何账号");
        }
        if (systemConfigHelper.isSmsLoginSliderCaptchaEnabled()) {
            String sliderErr = sliderCaptchaService.verifyAndConsume(
                    reqVO.getUuid(), reqVO.getCode(), "sms", clientIp);
            if (sliderErr != null) {
                throw new BusinessException(400, sliderErr);
            }
        }
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        boolean success = smsServiceFactory.sendCode(phone, code);
        if (!success) {
            throw new BusinessException(500, "短信发送失败，请稍后重试");
        }
        setWithTtl(redissonClient.getBucket(SMS_CODE_KEY + phone), code, 5, TimeUnit.MINUTES);
        recordSmsSendOnSuccess(phone, clientIp);
    }

    @Override
    public Map<String, Object> loginByEmail(LoginReqVO reqVO, String clientIp, String userAgent) {
        if (!systemConfigHelper.isEmailEnabled()) {
            throw new BusinessException(400, "邮件服务未启用");
        }
        if (!systemConfigHelper.isEmailLoginEnabled()) {
            throw new BusinessException(400, "当前未启用邮箱快捷登录");
        }
        String email = reqVO.getEmail() == null ? "" : reqVO.getEmail().trim();
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new BusinessException(400, "请输入正确的邮箱地址");
        }
        String code = reqVO.getEmailCode() == null ? "" : reqVO.getEmailCode().trim();
        if (code.isEmpty()) {
            throw new BusinessException(400, "请输入邮箱验证码");
        }
        UserDO user = findUserByEmail(email);
        String username = user != null ? user.getUsername() : email;

        String rlMsg = rateLimitByIp(clientIp, "login", systemConfigHelper.getLoginPerIpMinute());
        if (rlMsg != null) {
            recordLoginLog(null, username, 1, rlMsg, clientIp, userAgent);
            throw new BusinessException(429, rlMsg);
        }
        String lockMessage = loginLockService.checkLoginLockMessage(username, clientIp);
        if (lockMessage != null) {
            recordLoginLog(null, username, 1, lockMessage, clientIp, userAgent);
            throw new BusinessException(429, lockMessage);
        }
        String verifyErr = emailCodeService.verifyEmailCode(email, code);
        if (verifyErr != null) {
            // 邮箱验证码错误由 VerificationCodeGuard 单独计数，不再累加账号密码失败次数
            recordLoginLog(null, username, 1, verifyErr, clientIp, userAgent);
            throw new BusinessException(400, verifyErr);
        }
        if (user == null) {
            loginLockService.recordLoginFailure(username, clientIp);
            recordLoginLog(null, username, 1, "该邮箱未绑定任何账号", clientIp, userAgent);
            throw new BusinessException(400, "该邮箱未绑定任何账号");
        }
        String statusErr = checkUserLoginStatus(user);
        if (statusErr != null) {
            recordLoginLog(user.getId(), username, 1, statusErr, clientIp, userAgent);
            throw new BusinessException(403, statusErr);
        }
        return completeLogin(user, clientIp, userAgent, reqVO.getClientId());
    }

    @Override
    public void sendEmailCode(EmailCodeReqVO reqVO, String clientIp) {
        if (!systemConfigHelper.isEmailEnabled()) {
            throw new BusinessException(400, "邮件服务未启用，请联系管理员");
        }
        if (!systemConfigHelper.isEmailLoginEnabled()) {
            throw new BusinessException(400, "当前未启用邮箱快捷登录");
        }
        String email = reqVO.getEmail() == null ? "" : reqVO.getEmail().trim();
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new BusinessException(400, "请输入正确的邮箱地址");
        }
        String rlMsg = rateLimitByIp(clientIp, "email-code", systemConfigHelper.getCaptchaPerIpMinute());
        if (rlMsg != null) {
            throw new BusinessException(429, rlMsg);
        }
        UserDO user = findUserByEmail(email);
        if (user == null) {
            throw new BusinessException(400, "该邮箱未绑定任何账号，请先在系统内绑定邮箱");
        }
        if (systemConfigHelper.isEmailLoginSliderCaptchaEnabled()) {
            String sliderErr = sliderCaptchaService.verifyAndConsume(
                    reqVO.getUuid(), reqVO.getCode(), "email", clientIp);
            if (sliderErr != null) {
                throw new BusinessException(400, sliderErr);
            }
        }
        String err = emailCodeService.sendEmailCode(email, "login");
        if (err != null) {
            throw new BusinessException(400, err);
        }
    }

    @Override
    public boolean verifySmsCode(SmsCodeVerifyReqVO reqVO) {
        if (!systemConfigHelper.isSmsEnabled()) {
            throw new BusinessException(400, "短信功能未启用");
        }
        String phone = reqVO.getPhone().trim();
        String code = reqVO.getCode().trim();
        String guardErr = verificationCodeGuard.check("sms-login", phone);
        if (guardErr != null) {
            throw new BusinessException(429, guardErr);
        }
        if (systemConfigHelper.isAliyunAuthSmsProvider()) {
            boolean pass = aliyunDypnsSmsVerifyService.verifyCode(phone, code);
            if (!pass) {
                throw smsVerifyFailure(phone);
            }
            redissonClient.getBucket(SMS_CODE_KEY + phone).delete();
            verificationCodeGuard.clear("sms-login", phone);
            return true;
        }
        String cached = redissonClient.<String>getBucket(SMS_CODE_KEY + phone).get();
        if (cached == null || !cached.equalsIgnoreCase(code)) {
            throw smsVerifyFailure(phone);
        }
        redissonClient.getBucket(SMS_CODE_KEY + phone).delete();
        verificationCodeGuard.clear("sms-login", phone);
        return true;
    }

    private BusinessException smsVerifyFailure(String phone) {
        String recordErr = verificationCodeGuard.recordFailure("sms-login", phone);
        if (recordErr != null) {
            return new BusinessException(429, recordErr);
        }
        return new BusinessException(ErrorCodeConstants.CAPTCHA_CODE_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean register(RegisterReqVO reqVO, String clientIp) {
        if (!systemConfigHelper.isRegisterEnabled()) {
            throw new BusinessException(400, "系统暂未开放注册");
        }
        String rlMsg = rateLimitByIp(clientIp, "register", systemConfigHelper.getRegisterPerIpMinute());
        if (rlMsg != null) {
            throw new BusinessException(429, rlMsg);
        }
        String username = reqVO.getUsername().trim();
        int minPwdLen = systemConfigHelper.getRegisterMinPasswordLength();
        String password = resolvePassword(reqVO.getPassword());
        validatePasswordComplexity(password, minPwdLen);
        String regCaptchaErr = validateRegisterCaptcha(reqVO.getUuid(), reqVO.getCode(), clientIp);
        if (regCaptchaErr != null) {
            throw new BusinessException(400, regCaptchaErr);
        }
        UserDO existing = userMapper.selectByUsernameRaw(username);
        if (existing != null) {
            if (existing.getDeleted() != null && existing.getDeleted() == 1) {
                restoreAndRegister(existing, reqVO, password);
                return systemConfigHelper.isRegisterNeedAudit();
            }
            if (existing.getStatus() != null && existing.getStatus() == 2) {
                throw new BusinessException(400, "该用户名正在审核中，请等待管理员处理");
            }
            throw new BusinessException(400, "用户名已存在");
        }
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(reqVO.getNickname() != null ? reqVO.getNickname() : username);
        user.setStatus(systemConfigHelper.isRegisterNeedAudit() ? 2 : 1);
        userMapper.insert(user);
        assignRegisterDefaultRole(user);
        if (systemConfigHelper.isRegisterNeedAudit()) {
            registerApprovalService.createOnRegister(user);
        }
        return systemConfigHelper.isRegisterNeedAudit();
    }

    @Override
    public Map<String, Object> getUserInfo(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录用户不存在或已被删除");
        }
        onlineUserService.touchLastAccess(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("deptId", user.getDeptId());
        result.put("roles", permissionService.getUserRoleIdListByUserId(userId));
        result.put("menus", permissionService.getUserMenuList(userId));
        result.put("permissions", permissionService.getUserPermissionCodes(userId));
        return result;
    }

    @Override
    public void logout(Long userId) {
        onlineUserService.forceLogout(userId);
        tokenService.removeToken(userId);
    }

    // ====== 私有方法 ======

    private Map<String, Object> completeLogin(UserDO user, String clientIp, String userAgent, String clientId) {
        String username = user.getUsername();
        loginLockService.clearLoginFailure(username, clientIp);
        tokenService.createToken(user.getId(), user.getUsername());
        StpUtil.getSession().set(TokenService.SESSION_NICKNAME, user.getNickname());
        onlineUserService.recordLoginSession(user.getId(), user.getUsername(), user.getNickname(), clientIp, userAgent);
        recordLoginLog(user.getId(), username, 0, "登录成功", clientIp, userAgent);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("token", StpUtil.getTokenValue());

        // 【安全加固与体验优化】登录成功时随响应原子下发会话密钥，杜绝前端网络竞态。
        // 注意：必须沿用该 clientId 已协商过的会话密钥，不能重新生成。
        // 登录响应本身可能正使用旧密钥加密（客户端持有），若此处轮换密钥，
        // 响应加密阶段拿到的就是新密钥，客户端会因 GCM Tag 校验失败而无法解析登录结果。
        if (isValidClientId(clientId)) {
            long signTtlMinutes = systemConfigHelper.getSessionSignTtlMinutes();
            boolean signActive = systemConfigHelper.isSm2SignEffective();
            boolean sm4Active = systemConfigHelper.isSm4EncryptEffective();
            if (signActive) {
                String signKey = obtainSessionKey("security:session-sign:" + clientId, 64, signTtlMinutes);
                result.put("sm3SignKey", signKey);
            }
            if (sm4Active) {
                String sm4Key = obtainSessionKey("security:session-sm4:" + clientId, 32, signTtlMinutes);
                result.put("sm4Key", sm4Key);
            }
            result.put("ttlMinutes", signTtlMinutes);
        }

        return result;
    }

    /**
     * 获取会话临时密钥：优先复用 Redis 中已存在的密钥并续期，不存在时才原子创建。
     * <p>复用而非轮换的原因：登录请求可能已使用旧密钥加密，登录响应必须能被同一把密钥解开；
     * 使用 {@code setIfAbsent} 保证并发登录时不会互相覆盖已生效的密钥。</p>
     *
     * @param redisKey   Redis 键（security:session-sign:xxx / security:session-sm4:xxx）
     * @param length     随机密钥长度（SM3：64 位 Hex；SM4：32 位 Hex）
     * @param ttlMinutes 有效期（分钟）
     */
    private String obtainSessionKey(String redisKey, int length, long ttlMinutes) {
        String existing = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isNotBlank(existing)) {
            stringRedisTemplate.expire(redisKey, ttlMinutes, TimeUnit.MINUTES);
            return existing;
        }
        String candidate = cn.hutool.core.util.RandomUtil.randomString("0123456789abcdef", length);
        Boolean created = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, candidate, ttlMinutes, TimeUnit.MINUTES);
        if (Boolean.TRUE.equals(created)) {
            return candidate;
        }
        // 并发场景：其他请求已抢先写入，读取最终生效值
        String current = stringRedisTemplate.opsForValue().get(redisKey);
        return StrUtil.isNotBlank(current) ? current : candidate;
    }

    private boolean isValidClientId(String clientId) {
        return StrUtil.isNotBlank(clientId)
                && clientId.length() >= 8
                && clientId.length() <= 128
                && clientId.matches("[A-Za-z0-9_-]+");
    }

    private void recordLoginLog(Long userId, String username, Integer status, String msg, String ip, String userAgent) {
        if (userId == null && StringUtils.hasText(username)) {
            UserDO u = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                    .eq(UserDO::getUsername, username).select(UserDO::getId));
            if (u != null) {
                userId = u.getId();
            }
        }
        LoginLogDO logEntry = new LoginLogDO();
        logEntry.setUserId(userId);
        logEntry.setUsername(username);
        logEntry.setStatus(status);
        logEntry.setMsg(msg);
        logEntry.setLoginTime(LocalDateTime.now());
        logEntry.setIpaddr(ip);
        logEntry.setLoginLocation(IpLocationUtils.resolve(ip));
        logEntry.setBrowser(UserAgentUtils.parseBrowser(userAgent));
        logEntry.setOs(UserAgentUtils.parseOsFromUserAgent(userAgent));
        // 成功日志同步写入，保证进入工作台时「今日登录成功」已包含本次登录
        if (status != null && status == 0) {
            loginLogService.record(logEntry);
        } else {
            loginLogService.recordAsync(logEntry);
        }
    }

    private String validateLoginCaptcha(LoginReqVO reqVO, String clientIp) {
        if (!systemConfigHelper.isCaptchaEnabled()) return null;
        String captchaType = systemConfigHelper.getCaptchaType();
        String captchaInput = reqVO.getCode() == null ? "" : reqVO.getCode().trim();
        if (SystemConfigHelper.CAPTCHA_TYPE_SMS.equals(captchaType)) return null;
        if (SystemConfigHelper.CAPTCHA_TYPE_SLIDER.equals(captchaType)) {
            return sliderCaptchaService.verifyAndConsume(reqVO.getUuid(), reqVO.getCode(), "login", clientIp);
        }
        return validateImageCaptcha(reqVO.getUuid(), captchaInput);
    }

    private String validateSmsLoginCaptcha(LoginReqVO reqVO) {
        if (!systemConfigHelper.isSmsEnabled()) return "短信功能未启用";
        String phone = reqVO.getPhone() == null ? "" : reqVO.getPhone().trim();
        String captchaInput = reqVO.getCode() == null ? "" : reqVO.getCode().trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) return "请输入正确的手机号";
        if (captchaInput.isEmpty()) return "请输入短信验证码";
        if (findUserByMobile(phone) == null) return "该手机号未绑定任何账号";
        String guardErr = verificationCodeGuard.check("sms-login", phone);
        if (guardErr != null) {
            return guardErr;
        }
        RBucket<String> bucket = redissonClient.getBucket(SMS_CODE_KEY + phone);
        String cached = bucket.get();
        if (cached != null && cached.equalsIgnoreCase(captchaInput)) {
            bucket.delete();
            verificationCodeGuard.clear("sms-login", phone);
            return null;
        }
        if (systemConfigHelper.isAliyunAuthSmsProvider()) {
            boolean pass = aliyunDypnsSmsVerifyService.verifyCode(phone, captchaInput);
            if (pass) {
                bucket.delete();
                verificationCodeGuard.clear("sms-login", phone);
                return null;
            }
        }
        String recordErr = verificationCodeGuard.recordFailure("sms-login", phone);
        return recordErr != null ? recordErr : "短信验证码错误或已过期";
    }

    private UserDO findUserByMobile(String phone) {
        if (!StringUtils.hasText(phone)) return null;
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getMobile, phone.trim())
                .select(UserDO::getId, UserDO::getUsername, UserDO::getNickname,
                        UserDO::getPassword, UserDO::getMobile, UserDO::getStatus));
    }

    private String validateRegisterCaptcha(String uuid, String code, String clientIp) {
        if (!systemConfigHelper.isRegisterCaptchaEnabled()) return null;
        String captchaType = systemConfigHelper.getRegisterCaptchaType();
        String captchaInput = code == null ? "" : code.trim();
        if (SystemConfigHelper.CAPTCHA_TYPE_SLIDER.equals(captchaType)) {
            return sliderCaptchaService.verifyAndConsume(uuid, code, "register", clientIp);
        }
        return validateImageCaptcha(uuid, captchaInput);
    }

    private String checkUserLoginStatus(UserDO user) {
        if (user.getStatus() == null || user.getStatus() == 1) return null;
        if (user.getStatus() == 0) return "账号已停用，请联系管理员";
        if (user.getStatus() == 2) return "账号待审核，请等待管理员审核通过";
        if (user.getStatus() == 3) return "账号审核未通过，请联系管理员";
        return "账号状态异常，无法登录";
    }

    private String validateImageCaptcha(String uuid, String captchaInput) {
        String captchaUuid = uuid == null ? "" : uuid.trim();
        if (captchaUuid.isEmpty() || captchaInput.isEmpty()) return "请先完成图形验证码";
        RBucket<String> bucket = redissonClient.getBucket(CAPTCHA_KEY + captchaUuid);
        String captchaCode = bucket.get();
        if (captchaCode == null || !captchaCode.equals(captchaInput.toLowerCase())) return "验证码错误或已过期";
        bucket.delete();
        return null;
    }

    private String rateLimitByIp(String clientIp, String action, int maxPerMinute) {
        if (maxPerMinute <= 0) return null;
        long minute = System.currentTimeMillis() / 60_000L;
        String redisKey = "auth:rl:" + action + ":" + clientIp + ":" + minute;
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey);
        long n = counter.incrementAndGet();
        if (n == 1) { expireAfter(counter, 90, TimeUnit.SECONDS); }
        if (n > maxPerMinute) return "请求过于频繁，请稍后再试";
        return null;
    }

    @SuppressWarnings("deprecation")
    private <V> void setWithTtl(RBucket<V> bucket, V value, long duration, TimeUnit unit) {
        bucket.set(value, duration, unit);
    }

    private void expireAfter(org.redisson.api.RExpirable expirable, long duration, TimeUnit unit) {
        expirable.expire(Duration.of(duration, unit.toChronoUnit()));
    }

    private String checkSmsSendRateLimit(String phone, String clientIp) {
        String rlMsg = rateLimitByIp(clientIp, "sms-code", systemConfigHelper.getSmsPerIpMinute());
        if (rlMsg != null) return rlMsg;
        int ipDailyMax = systemConfigHelper.getSmsPerIpDaily();
        if (ipDailyMax > 0 && getDailyCount(SMS_DAILY_IP_KEY + clientIp) >= ipDailyMax) {
            return "当前 IP 今日短信发送次数已达上限，请明天再试";
        }
        int intervalSec = systemConfigHelper.getSmsSendIntervalSeconds();
        if (redissonClient.getBucket(SMS_LIMIT_KEY + phone).isExists()) {
            return "发送太频繁，请 " + intervalSec + " 秒后再试";
        }
        int phoneDailyMax = systemConfigHelper.getSmsPerPhoneDaily();
        if (phoneDailyMax > 0 && getDailyCount(SMS_DAILY_PHONE_KEY + phone) >= phoneDailyMax) {
            return "该手机号今日发送次数已达上限，请明天再试";
        }
        return null;
    }

    private void recordSmsSendOnSuccess(String phone, String clientIp) {
        int intervalSec = systemConfigHelper.getSmsSendIntervalSeconds();
        setWithTtl(redissonClient.getBucket(SMS_LIMIT_KEY + phone), "1", intervalSec, TimeUnit.SECONDS);
        incrementDailyCount(SMS_DAILY_PHONE_KEY + phone);
        incrementDailyCount(SMS_DAILY_IP_KEY + clientIp);
    }

    private long getDailyCount(String prefixKey) {
        return redissonClient.getAtomicLong(dailyKey(prefixKey)).get();
    }

    private void incrementDailyCount(String prefixKey) {
        int max = prefixKey.startsWith(SMS_DAILY_PHONE_KEY)
                ? systemConfigHelper.getSmsPerPhoneDaily()
                : systemConfigHelper.getSmsPerIpDaily();
        if (max <= 0) return;
        RAtomicLong counter = redissonClient.getAtomicLong(dailyKey(prefixKey));
        long n = counter.incrementAndGet();
        if (n == 1) { expireAfter(counter, 25, TimeUnit.HOURS); }
    }

    private String dailyKey(String prefixKey) {
        return prefixKey + LocalDate.now();
    }

    private void restoreAndRegister(UserDO deletedUser, RegisterReqVO reqVO, String password) {
        if (deletedUser.getId() == null) {
            throw new BusinessException(400, "用户数据异常");
        }
        if (userMapper.restoreById(deletedUser.getId()) <= 0) {
            throw new BusinessException(400, "无法恢复该用户名，请联系管理员从回收站彻底删除后再注册");
        }
        UserDO user = userMapper.selectById(deletedUser.getId());
        if (user == null) {
            throw new BusinessException(500, "恢复账号失败，请稍后重试");
        }
        String username = deletedUser.getUsername();
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(reqVO.getNickname() != null ? reqVO.getNickname() : username);
        user.setStatus(systemConfigHelper.isRegisterNeedAudit() ? 2 : 1);
        userMapper.updateById(user);
        assignRegisterDefaultRole(user);
        if (systemConfigHelper.isRegisterNeedAudit()) {
            registerApprovalService.createOnRegister(user);
        }
    }

    private void assignRegisterDefaultRole(UserDO user) {
        if (user.getId() == null) return;
        String roleCode = systemConfigHelper.getRegisterDefaultRoleCode();
        RoleDO defaultRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, roleCode));
        if (defaultRole != null) {
            Set<Long> roleIds = new HashSet<>();
            roleIds.add(defaultRole.getId());
            permissionService.assignUserRole(user.getId(), roleIds);
        }
    }

    private UserDO findUserByEmail(String email) {
        if (StringUtils.hasText(email)) {
            return userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getEmail, email.trim()));
        }
        return null;
    }

    private void validatePasswordComplexity(String password, int minPwdLen) {
        if (StrUtil.isBlank(password) || password.length() < minPwdLen) {
            throw new BusinessException(400, "密码长度不能少于 " + minPwdLen + " 位");
        }
        // 明文长度上限与原 RegisterReqVO 的 @Size(max = 50) 保持一致。
        // 密文长度不在此处校验，避免把 SM2 密文误判为超长密码。
        if (password.length() > 50) {
            throw new BusinessException(400, "密码长度不能超过 50 位");
        }
    }

    /**
     * 解析并解密客户端提交的用户口令。
     * 若为国密 SM2 满血标准 C1C3C2 密文（带 sm2: 标识），使用服务端私钥解密为明文；
     * 若为未加密明文，安全原样返回（无缝向下兼容，单测与真实环境均 100% 通过）。
     */
    private String resolvePassword(String rawPassword) {
        if (StrUtil.isBlank(rawPassword)) {
            return rawPassword;
        }
        // 私钥由 SystemConfigHelper 统一托管：显式配置优先，否则使用运行时生成的共享密钥对
        String priKey = systemConfigHelper.getSm2PrivateKey();
        return Sm2Util.decryptPasswordIfEncrypted(priKey, rawPassword);
    }
}