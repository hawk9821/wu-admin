package com.admin.server.modules.system.service.user.impl;

import com.admin.server.common.exception.BusinessException;
import com.admin.server.modules.system.enums.ErrorCodeConstants;
import com.admin.server.framework.security.core.service.TokenService;
import com.admin.server.modules.system.api.user.vo.UserCreateReqVO;
import com.admin.server.modules.system.api.user.vo.UserUpdateReqVO;
import com.admin.server.modules.system.dal.dataobject.loginlog.LoginLogDO;
import com.admin.server.modules.system.dal.dataobject.user.UserDO;
import com.admin.server.modules.system.dal.mysql.dept.DeptMapper;
import com.admin.server.modules.system.dal.mysql.loginlog.LoginLogMapper;
import com.admin.server.modules.system.dal.mysql.post.PostMapper;
import com.admin.server.modules.system.dal.mysql.user.UserMapper;
import com.admin.server.modules.system.dal.mysql.user.UserPostMapper;
import com.admin.server.modules.system.dal.dataobject.permission.RoleDO;
import com.admin.server.modules.system.dal.mysql.permission.RoleMapper;
import com.admin.server.modules.system.service.auth.LoginLockService;
import com.admin.server.modules.system.service.config.SystemConfigHelper;
import com.admin.server.modules.system.service.permission.DataScopeService;
import com.admin.server.modules.system.service.permission.PermissionService;
import com.admin.server.testsupport.MybatisLambdaTestBase;
import com.admin.server.testsupport.ServiceTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest extends MybatisLambdaTestBase {

    @Mock
    private UserMapper userMapper;
    @Mock
    private DeptMapper deptMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserPostMapper userPostMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginLockService loginLockService;
    @Mock
    private LoginLogMapper loginLogMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SystemConfigHelper systemConfigHelper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void stubPasswordDecryption() {
        // 默认口令未加密，原样返回；加密场景由具体用例覆盖。
        // 部分用例不涉及口令，使用 lenient 避免严格模式判定为多余桩。
        lenient().when(systemConfigHelper.decryptPasswordIfEncrypted(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        // 写操作新增了按 ID 的数据范围校验，测试默认按超管放行
        lenient().when(dataScopeService.isUserVisible(anyLong())).thenReturn(true);
        lenient().when(dataScopeService.isDeptVisible(anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("createUser：创建用户并分配角色")
    void createUser_assignsRole() {
        UserCreateReqVO req = new UserCreateReqVO();
        req.setUsername("bob");
        req.setPassword("secret123");
        req.setNickname("Bob");
        req.setRoleId(2L);

        when(passwordEncoder.encode("secret123")).thenReturn("$2a$encoded");
        doAnswer(inv -> {
            UserDO user = inv.getArgument(0);
            user.setId(10L);
            return 1;
        }).when(userMapper).insert(any(UserDO.class));

        Long id = userService.createUser(req);

        assertEquals(10L, id);
        verify(permissionService).assignUserRole(10L, Set.of(2L));
        verify(userPostMapper).deleteByUserId(10L);
    }

    @Test
    @DisplayName("createUser：未传入角色时默认分配系统默认角色（普通用户）")
    void createUser_noRole_assignsDefaultRole() {
        UserCreateReqVO req = new UserCreateReqVO();
        req.setUsername("charlie");
        req.setPassword("secret123");
        req.setNickname("Charlie");
        req.setRoleId(null);

        RoleDO userRole = new RoleDO();
        userRole.setId(2L);
        userRole.setCode("user");

        when(systemConfigHelper.getRegisterDefaultRoleCode()).thenReturn("user");
        when(roleMapper.selectOne(any())).thenReturn(userRole);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$encoded");
        doAnswer(inv -> {
            UserDO user = inv.getArgument(0);
            user.setId(20L);
            return 1;
        }).when(userMapper).insert(any(UserDO.class));

        Long id = userService.createUser(req);

        assertEquals(20L, id);
        verify(permissionService).assignUserRole(20L, Set.of(2L));
    }

    @Test
    @DisplayName("createUser：密码长度小于 6 位抛 400")
    void createUser_invalidPasswordLength() {
        UserCreateReqVO req = new UserCreateReqVO();
        req.setUsername("alice");
        req.setPassword("123");
        req.setNickname("Alice");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.createUser(req));
        assertEquals(400, ex.getCode());
        assertEquals("密码长度 6~50 个字符", ex.getMessage());
    }

    @Test
    @DisplayName("updateUser：用户不存在抛 404")
    void updateUser_notFound() {
        UserUpdateReqVO req = new UserUpdateReqVO();
        req.setId(99L);
        req.setNickname("n");
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateUser(req));

        assertEquals(ErrorCodeConstants.USER_NOT_EXISTS.code(), ex.getCode());
        verify(userMapper, never()).updateById(any(UserDO.class));
    }

    @Test
    @DisplayName("updateStatus：用户不存在抛 404")
    void updateStatus_notFound() {
        when(userMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateStatus(1L, 0));

        assertEquals(ErrorCodeConstants.USER_NOT_EXISTS.code(), ex.getCode());
    }

    @Test
    @DisplayName("resetPassword：重置密码并加密")
    void resetPassword_encodesAndUpdates() {
        UserDO user = ServiceTestFixtures.user(1L, "alice", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.encode("newPass")).thenReturn("$2a$new");

        userService.resetPassword(1L, "newPass");

        verify(userMapper).updateById(argThat((UserDO u) -> "$2a$new".equals(u.getPassword())));
    }

    @Test
    @DisplayName("restore：回收站用户不存在抛 404")
    void restore_notFound() {
        when(userMapper.selectDeletedById(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.restore(5L));

        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("kickOut：调用 TokenService 踢出用户")
    void kickOut_delegatesToTokenService() {
        when(userMapper.selectById(8L)).thenReturn(ServiceTestFixtures.user(8L, "bob", 1));
        userService.kickOut(8L);
        verify(tokenService).removeToken(8L);
    }

    @Test
    @DisplayName("unlockLogin：用户不存在抛 404")
    void unlockLogin_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.unlockLogin(99L));
        assertEquals(ErrorCodeConstants.USER_NOT_EXISTS.code(), ex.getCode());
    }

    @Test
    @DisplayName("unlockLogin：解除账号并清除最近登录 IP 锁定")
    void unlockLogin_success() {
        UserDO user = ServiceTestFixtures.user(1L, "alice", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        LoginLogDO log = new LoginLogDO();
        log.setUsername("alice");
        log.setIpaddr("192.168.1.10");
        when(loginLogMapper.selectList(any())).thenReturn(List.of(log));

        userService.unlockLogin(1L);

        verify(loginLockService).unlockUser("alice");
        verify(loginLockService).unlockIp("192.168.1.10");
    }
}
