package com.hjgd.plm.dataio.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.dataio.model.ImportResult;
import com.hjgd.plm.dataio.model.SelfCheckResult;
import com.hjgd.plm.dataio.service.DataIoService;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "通用导入导出与自检")
@RestController
@RequestMapping("/data-io")
@RequiredArgsConstructor
public class DataIoController {

    private final DataIoService service;

    @Operation(summary = "可用模块列表")
    @GetMapping("/modules")
    public Result<List<Map<String, Object>>> modules() {
        return Result.success(service.listModules());
    }

    @Operation(summary = "下载导入模板(.xlsx)")
    @GetMapping("/{module}/template")
    public ResponseEntity<byte[]> template(@PathVariable String module) {
        byte[] body = service.template(module);
        return download(body, service.def(module).getName() + "_导入模板_" + LocalDate.now() + ".xlsx");
    }

    @Operation(summary = "导出数据(.xlsx)")
    @OperationLog(value = "数据导出")
    @GetMapping("/{module}/export")
    public ResponseEntity<byte[]> export(@PathVariable String module) {
        byte[] body = service.export(module);
        return download(body, service.def(module).getName() + "_" + LocalDate.now() + ".xlsx");
    }

    @Operation(summary = "导入数据(支持 dryRun 预检)")
    @OperationLog(value = "数据导入")
    @PostMapping("/{module}/import")
    public Result<ImportResult> importData(@PathVariable String module,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "false") boolean dryRun) {
        return Result.success(service.importExcel(module, file, dryRun));
    }

    @Operation(summary = "模块自检")
    @GetMapping("/{module}/selfcheck")
    public Result<SelfCheckResult> selfCheck(@PathVariable String module) {
        return Result.success(service.selfCheck(module));
    }

    private ResponseEntity<byte[]> download(byte[] body, String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
