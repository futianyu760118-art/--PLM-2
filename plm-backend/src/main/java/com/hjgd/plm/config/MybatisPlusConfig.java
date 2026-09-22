package com.hjgd.plm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    /**
     * PostgreSQL TIMESTAMPTZ → LocalDateTime 全局 TypeHandler。
     * 通过 BeanPostProcessor 在 SqlSessionFactory 初始化后注入到 MyBatis Configuration，
     * 覆盖默认 LocalDateTimeTypeHandler（默认走 getObject(col, LocalDateTime.class) 触发 PG JDBC 拒绝）。
     * 本 handler 改走 getTimestamp()（PG JDBC 按 URL TZ 参数转换到本地时区）再 .toLocalDateTime()。
     */
    @Bean
    public BeanPostProcessor timestamptzTypeHandlerInjector() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SqlSessionFactory ssf) {
                    org.apache.ibatis.session.Configuration cfg = ssf.getConfiguration();
                    TypeHandler<LocalDateTime> handler = new TimestamptzToLocalDateTimeHandler();
                    // 覆盖默认 LocalDateTimeTypeHandler
                    cfg.getTypeHandlerRegistry().register(LocalDateTime.class, handler);
                    cfg.getTypeHandlerRegistry().register(java.util.Date.class, new DateTimestampHandler());
                }
                return bean;
            }
        };
    }

    @MappedTypes(LocalDateTime.class)
    public static class TimestamptzToLocalDateTimeHandler extends BaseTypeHandler<LocalDateTime> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType) throws SQLException {
            ps.setTimestamp(i, java.sql.Timestamp.valueOf(parameter));
        }

        @Override
        public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
            java.sql.Timestamp ts = rs.getTimestamp(columnName);
            return ts == null ? null : ts.toLocalDateTime();
        }

        @Override
        public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            java.sql.Timestamp ts = rs.getTimestamp(columnIndex);
            return ts == null ? null : ts.toLocalDateTime();
        }

        @Override
        public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            java.sql.Timestamp ts = cs.getTimestamp(columnIndex);
            return ts == null ? null : ts.toLocalDateTime();
        }
    }

    /** Date 类型同样走 getTimestamp, 避免 PG JDBC TIMESTAMPTZ → Date.getTime() 异常 */
    @MappedTypes(java.util.Date.class)
    public static class DateTimestampHandler extends BaseTypeHandler<java.util.Date> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, java.util.Date parameter, JdbcType jdbcType) throws SQLException {
            ps.setTimestamp(i, new java.sql.Timestamp(parameter.getTime()));
        }

        @Override
        public java.util.Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
            java.sql.Timestamp ts = rs.getTimestamp(columnName);
            return ts == null ? null : new java.util.Date(ts.getTime());
        }

        @Override
        public java.util.Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            java.sql.Timestamp ts = rs.getTimestamp(columnIndex);
            return ts == null ? null : new java.util.Date(ts.getTime());
        }

        @Override
        public java.util.Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            java.sql.Timestamp ts = cs.getTimestamp(columnIndex);
            return ts == null ? null : new java.util.Date(ts.getTime());
        }
    }
}
