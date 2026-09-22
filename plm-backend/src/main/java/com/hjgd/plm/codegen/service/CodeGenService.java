package com.hjgd.plm.codegen.service;

import java.util.Map;

public interface CodeGenService {

    String preview(String objectType, Map<String, Object> context);

    String allocate(String objectType, Map<String, Object> context, String objectId, String source);
}
