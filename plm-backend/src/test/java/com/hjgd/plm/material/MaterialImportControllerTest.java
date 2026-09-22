package com.hjgd.plm.material;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.material.controller.MaterialImportController;
import com.hjgd.plm.material.controller.MaterialImportController.ImportBody;
import com.hjgd.plm.material.controller.MaterialImportController.ImportRow;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.material.service.MaterialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 物料批量导入：类型推断 / 去重 / 错误隔离。
 */
@DisplayName("物料批量导入 V1.1")
@ExtendWith(MockitoExtension.class)
class MaterialImportControllerTest {

    @Mock private MaterialService materialService;
    @InjectMocks private MaterialImportController controller;

    @Test
    @DisplayName("显式 materialType 优先；有 productType 无 type→FINISHED；都没有→STANDARD")
    void shouldInferMaterialType() {
        ImportBody body = new ImportBody();
        ImportRow explicit = row("HJ_A", "塑件A", "PLASTIC", null);
        ImportRow byProduct = row("HJ_B", "投光灯B", null, "FL");
        ImportRow fallback = row(null, "标准件C", null, null);
        body.setProducts(List.of(explicit, byProduct, fallback));

        when(materialService.checkPartNoDuplicate(eq("HJ_A"), isNull())).thenReturn(false);
        when(materialService.checkPartNoDuplicate(eq("HJ_B"), isNull())).thenReturn(false);
        when(materialService.create(any(MaterialDTO.class))).thenAnswer(inv -> new Material());

        controller.importParts(body);

        // 校验三行的类型推断：通过 captor 校验 dto.materialType
        org.mockito.ArgumentCaptor<MaterialDTO> cap = org.mockito.ArgumentCaptor.forClass(MaterialDTO.class);
        verify(materialService, times(3)).create(cap.capture());
        assertEquals(MaterialType.PLASTIC, cap.getAllValues().get(0).getMaterialType());
        assertEquals(MaterialType.FINISHED, cap.getAllValues().get(1).getMaterialType());
        assertEquals(MaterialType.STANDARD, cap.getAllValues().get(2).getMaterialType());
    }

    @Test
    @DisplayName("已存在 partNo 跳过；失败行进 errors 不中断")
    void shouldSkipDuplicateAndCollectErrors() {
        ImportBody body = new ImportBody();
        body.setProducts(List.of(
                row("DUP", "重复件", "STANDARD", null),
                row("OK1", "正常件", "STANDARD", null),
                row("BAD", "坏件", "STANDARD", null)
        ));
        when(materialService.checkPartNoDuplicate(eq("DUP"), isNull())).thenReturn(true);
        when(materialService.checkPartNoDuplicate(eq("OK1"), isNull())).thenReturn(false);
        when(materialService.checkPartNoDuplicate(eq("BAD"), isNull())).thenReturn(false);
        when(materialService.create(any(MaterialDTO.class)))
                .thenReturn(new Material())
                .thenThrow(new BusinessException("校验失败"));

        Map<String, Object> result = controller.importParts(body).getData();

        assertEquals(1, result.get("success"));
        assertEquals(1, result.get("skipped"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) result.get("errors");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("BAD"));
    }

    private ImportRow row(String partNo, String name, String type, String productType) {
        ImportRow r = new ImportRow();
        r.setPartNo(partNo);
        r.setMaterialName(name);
        r.setMaterialType(type);
        r.setProductType(productType);
        return r;
    }
}
