package com.hjgd.plm.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hjgd.plm.config.MybatisPlusConfig;
import com.hjgd.plm.material.entity.Material;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归 D4: 逻辑删除位必须由 insertFill 落 0。
 *
 * @TableLogic 会给查询拼上 WHERE deleted=0, 新行若插入成 NULL 则永远查不到
 * (getByPartNo 静默返回 null)。这里直接驱动 MetaObjectHandler, 不依赖数据库。
 */
@DisplayName("回归 D4: insertFill 补逻辑删除位")
class LogicDeleteInsertFillTest {

    /** TableInfo 是 lambda 列名解析与 fill 字段清单的来源, 单测里需显式初始化 */
    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    @Test
    @DisplayName("继承 BaseEntity 的实体: insert 时 deleted 被填为 0")
    void insertFillSetsDeletedToZero() {
        initTableInfo(Material.class);
        MetaObjectHandler handler = new MybatisPlusConfig().metaObjectHandler();

        Material material = new Material();
        material.setPartNo("HJ202607040001");

        handler.insertFill(SystemMetaObject.forObject(material));

        assertEquals(0, material.getDeleted(), "新行 deleted 必须为 0, 否则 WHERE deleted=0 匹配不到该行");
    }

    @Test
    @DisplayName("已显式赋值的 deleted 不被覆盖")
    void insertFillKeepsExplicitDeleted() {
        initTableInfo(Material.class);
        MetaObjectHandler handler = new MybatisPlusConfig().metaObjectHandler();

        Material material = new Material();
        material.setDeleted(1);

        handler.insertFill(SystemMetaObject.forObject(material));

        assertEquals(1, material.getDeleted());
    }

    @Test
    @DisplayName("createdAt/updatedAt 仍按原样填充")
    void insertFillKeepsTimestamps() {
        initTableInfo(Material.class);
        MetaObjectHandler handler = new MybatisPlusConfig().metaObjectHandler();

        Material material = new Material();
        handler.insertFill(SystemMetaObject.forObject(material));

        assertNotNull(material.getCreatedAt(), "createdAt 应被填充");
        assertNotNull(material.getUpdatedAt(), "updatedAt 应被填充");
    }
}
