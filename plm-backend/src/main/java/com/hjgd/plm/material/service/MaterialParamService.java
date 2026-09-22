package com.hjgd.plm.material.service;

import com.hjgd.plm.material.dto.MaterialParamView;

import java.util.List;
import java.util.Map;

public interface MaterialParamService {

    /** 返回该料号参数(模板定义 + 实际值 + ENUM 选项，按模板顺序) */
    List<MaterialParamView> listForPart(String partNo);

    /** 批量保存参数值(upsert) */
    void saveValues(String partNo, Map<String, String> values);
}
