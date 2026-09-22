package com.hjgd.plm.ecn.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.ecn.entity.EcnImpact;
import com.hjgd.plm.ecn.service.EcnImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "V1 ECN影响面")
@RestController
@RequestMapping("/v1/ecns")
@RequiredArgsConstructor
public class EcnImpactController {

    private final EcnImpactService ecnImpactService;

    @Operation(summary = "查询ECN影响清单")
    @GetMapping("/{ecnId}/impacts")
    public Result<List<EcnImpact>> list(@PathVariable Long ecnId) {
        return Result.success(ecnImpactService.listByEcn(ecnId));
    }

    @Operation(summary = "保存影响清单")
    @PostMapping("/{ecnId}/impacts")
    public Result<List<EcnImpact>> save(@PathVariable Long ecnId,
                                        @RequestParam String ecnNo,
                                        @RequestBody ImpactBody body) {
        return Result.success(ecnImpactService.saveImpacts(ecnId, ecnNo, body.getImpactTypes()));
    }

    @Data
    public static class ImpactBody {
        private List<String> impactTypes;
    }
}
