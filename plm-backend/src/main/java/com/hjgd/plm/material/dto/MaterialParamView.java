package com.hjgd.plm.material.dto;

import com.hjgd.plm.template.service.TemplateResolverService.DictOption;
import lombok.Data;

import java.util.List;

@Data
public class MaterialParamView {
    private String paramKey;
    private String paramName;
    private String unit;
    private String dataType;
    private String dictType;
    private boolean required;
    private String dqSeverity;
    private int sortOrder;
    private String value;
    private List<DictOption> options;
}
