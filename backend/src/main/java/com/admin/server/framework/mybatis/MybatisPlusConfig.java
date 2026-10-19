package com.admin.server.framework.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局插件配置
 * <p>仅保留分页插件；数据权限（组织隔离）在业务查询层按当前登录用户的角色数据范围显式拼接，
 * 避免全局 SQL 改写误伤定时任务、登录认证等无请求上下文的场景。</p>
 */
@Configuration
public class MybatisPlusConfig {

    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件 (设置单页最大限制，防御超大 pageSize 造成的深度慢查询与内存溢出)
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
