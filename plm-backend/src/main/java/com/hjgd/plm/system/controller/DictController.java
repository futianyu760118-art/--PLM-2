package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysDict;
import com.hjgd.plm.system.mapper.SysDictMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "字典管理")
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final SysDictMapper dictMapper;

    @Operation(summary = "按类型查询字典")
    @GetMapping("/type/{type}")
    public Result<List<SysDict>> byType(@PathVariable String type) {
        return Result.success(dictMapper.selectList(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictType, type)
                        .eq(SysDict::getStatus, 1)
                        .orderByAsc(SysDict::getSortOrder)));
    }

    @Operation(summary = "批量查询字典(返回 Map<type, List>)")
    @GetMapping("/types")
    public Result<Map<String, List<SysDict>>> types(@RequestParam List<String> types) {
        List<SysDict> all = dictMapper.selectList(
                new LambdaQueryWrapper<SysDict>()
                        .in(SysDict::getDictType, types)
                        .eq(SysDict::getStatus, 1)
                        .orderByAsc(SysDict::getSortOrder));
        Map<String, List<SysDict>> grouped = all.stream()
                .collect(Collectors.groupingBy(SysDict::getDictType));
        return Result.success(grouped);
    }

    @Operation(summary = "全部分页")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String dictType) {
        LambdaQueryWrapper<SysDict> w = new LambdaQueryWrapper<>();
        w.eq(dictType != null && !dictType.isEmpty(), SysDict::getDictType, dictType)
                .orderByAsc(SysDict::getDictType).orderByAsc(SysDict::getSortOrder);
        var page = dictMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), w);
        return Result.success(Map.of("records", page.getRecords(), "total", page.getTotal()));
    }

    @Operation(summary = "新增字典")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SysDict> create(@RequestBody SysDict dict) {
        dictMapper.insert(dict);
        return Result.success(dict);
    }

    @Operation(summary = "修改字典")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public Result<SysDict> update(@RequestBody SysDict dict) {
        dictMapper.updateById(dict);
        return Result.success(dict);
    }

    @Operation(summary = "删除字典")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictMapper.deleteById(id);
        return Result.success();
    }
}
