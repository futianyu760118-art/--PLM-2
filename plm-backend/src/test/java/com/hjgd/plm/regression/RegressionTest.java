package com.hjgd.plm.regression;

import com.hjgd.plm.common.BaseEntity;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.material.entity.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试 - 捕获部署过程中发现的问题,防止复发
 */
@DisplayName("回归测试 (防复发)")
class RegressionTest {

    @Nested
    @DisplayName("Bug: BaseEntity deleted 字段缺失导致查询失败")
    class DeletedColumnRegression {

        @Test
        @DisplayName("所有继承 BaseEntity 的实体必须有 deleted 字段")
        void allBaseEntitySubclassesShouldHaveDeletedField() {
            Class<?>[] entities = {
                com.hjgd.plm.material.entity.Material.class,
                com.hjgd.plm.bom.entity.Bom.class,
                com.hjgd.plm.ecn.entity.Ecn.class,
                com.hjgd.plm.system.entity.SysUser.class,
                com.hjgd.plm.system.entity.SysRole.class,
            };
            for (Class<?> clazz : entities) {
                assertTrue(hasFieldInHierarchy(clazz, "deleted"),
                    clazz.getSimpleName() + " 缺少 deleted 字段 (BaseEntity @TableLogic 需要)");
            }
        }

        private boolean hasFieldInHierarchy(Class<?> clazz, String fieldName) {
            while (clazz != null) {
                try {
                    clazz.getDeclaredField(fieldName);
                    return true;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            return false;
        }

        @Test
        @DisplayName("BaseEntity 的 deleted 字段必须标注 @TableLogic")
        void deletedFieldShouldHaveTableLogicAnnotation() throws NoSuchFieldException {
            Field deletedField = BaseEntity.class.getDeclaredField("deleted");
            assertNotNull(deletedField.getAnnotation(
                com.baomidou.mybatisplus.annotation.TableLogic.class),
                "BaseEntity.deleted 必须标注 @TableLogic");
        }
    }

    @Nested
    @DisplayName("Bug: BCrypt 密码哈希不匹配")
    class PasswordHashRegression {

        @Test
        @DisplayName("BCryptPasswordEncoder 能正确编码和验证 admin@123")
        void bcryptShouldEncodeAndMatch() {
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            String hash = encoder.encode("admin@123");
            assertTrue(encoder.matches("admin@123", hash), "编码后的哈希必须能匹配原文");
            assertFalse(encoder.matches("wrong", hash), "错误密码不能匹配");
        }

        @Test
        @DisplayName("旧的错误哈希不能匹配 admin@123")
        void oldWrongHashShouldNotMatch() {
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            String wrongHash = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";
            assertFalse(encoder.matches("admin@123", wrongHash),
                "旧哈希不应匹配 admin@123 (这就是部署时发现的bug)");
        }
    }

    @Nested
    @DisplayName("Bug: @EnumValue 误用在 String 字段")
    class EnumValueRegression {

        @Test
        @DisplayName("Bom.status 不应有 @EnumValue (String 类型)")
        void bomStatusShouldNotHaveEnumValue() throws NoSuchFieldException {
            Field statusField = com.hjgd.plm.bom.entity.Bom.class.getDeclaredField("status");
            assertNull(statusField.getAnnotation(com.baomidou.mybatisplus.annotation.EnumValue.class),
                "String 类型字段不应标注 @EnumValue");
        }

        @Test
        @DisplayName("PlmFile.visibility 不应有 @EnumValue")
        void plmFileVisibilityShouldNotHaveEnumValue() throws NoSuchFieldException {
            Field field = com.hjgd.plm.file.entity.PlmFile.class.getDeclaredField("visibility");
            assertNull(field.getAnnotation(com.baomidou.mybatisplus.annotation.EnumValue.class),
                "String 类型字段不应标注 @EnumValue");
        }

        @Test
        @DisplayName("Material.status 应该是枚举类型")
        void materialStatusShouldBeEnum() throws NoSuchFieldException {
            Field field = Material.class.getDeclaredField("status");
            assertEquals(com.hjgd.plm.material.enums.MaterialStatus.class, field.getType(),
                "Material.status 必须是 MaterialStatus 枚举");
        }
    }

    @Nested
    @DisplayName("Bug: 统一响应格式")
    class ResultFormatRegression {

        @Test
        @DisplayName("Result.success() 返回 code=200")
        void successShouldReturn200() {
            Result<String> r = Result.success("test");
            assertEquals(200, r.getCode());
            assertTrue(r.isSuccess());
            assertEquals("test", r.getData());
        }

        @Test
        @DisplayName("Result.failed() 返回错误码")
        void failedShouldReturnErrorCode() {
            Result<Void> r = Result.failed(ResultCode.PART_NO_DUPLICATE);
            assertEquals(10001, r.getCode());
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("所有 ResultCode 的 code 和 message 不为空")
        void allResultCodesShouldBeValid() {
            for (ResultCode rc : ResultCode.values()) {
                assertTrue(rc.getCode() > 0, rc.name() + " 的 code 必须 > 0");
                assertNotNull(rc.getMessage());
                assertFalse(rc.getMessage().isEmpty(), rc.name() + " 的 message 不能为空");
            }
        }
    }
}
