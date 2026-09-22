package com.hjgd.plm.material.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.material.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "V1 物料导入")
@RestController
@RequestMapping("/v1/parts")
@RequiredArgsConstructor
public class MaterialImportController {

    private final MaterialService materialService;

    @Operation(summary = "批量导入物料")
    @PreAuthorize("hasAuthority('material:add')")
    @PostMapping("/import")
    public Result<Map<String, Object>> importParts(@RequestBody ImportBody body) {
        int success = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();
        if (body.getProducts() != null) {
            for (ImportRow row : body.getProducts()) {
                try {
                    if (row.getPartNo() != null && materialService.checkPartNoDuplicate(row.getPartNo(), null)) {
                        skip++;
                        continue;
                    }
                    MaterialDTO dto = new MaterialDTO();
                    dto.setPartNo(row.getPartNo());
                    dto.setMaterialName(row.getMaterialName() != null ? row.getMaterialName() : row.getNameZh());
                    dto.setNameEn(row.getNameEn());
                    dto.setMaterialType(parseType(row.getMaterialType(), row.getProductType()));
                    dto.setProductType(row.getProductType());
                    dto.setSpecification(row.getSpecification());
                    dto.setUnit(row.getUnit() != null ? row.getUnit() : "PCS");
                    dto.setProjectNo(row.getProjectNo());
                    materialService.create(dto);
                    success++;
                } catch (Exception e) {
                    errors.add((row.getPartNo() != null ? row.getPartNo() : "?") + ": " + e.getMessage());
                    log.warn("import row failed: {}", e.getMessage());
                }
            }
        }
        return Result.success(Map.of("success", success, "skipped", skip, "errors", errors));
    }

    private MaterialType parseType(String type, String productType) {
        if (type != null) {
            try {
                return MaterialType.valueOf(type.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        if (productType != null && !productType.isBlank()) {
            return MaterialType.FINISHED;
        }
        return MaterialType.STANDARD;
    }

    @Data
    public static class ImportBody {
        private List<ImportRow> products;
    }

    @Data
    public static class ImportRow {
        private String partNo;
        private String materialName;
        private String nameZh;
        private String nameEn;
        private String materialType;
        private String productType;
        private String specification;
        private String unit;
        private String projectNo;
    }
}
