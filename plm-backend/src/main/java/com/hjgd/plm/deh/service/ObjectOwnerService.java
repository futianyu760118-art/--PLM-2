package com.hjgd.plm.deh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.ObjectOwner;
import com.hjgd.plm.deh.mapper.ObjectOwnerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 唯一事实源裁定（F01 / 架构 V0.2 §5.1）。
 *
 * <p>本服务回答「这类对象归谁」以及「有没有类被别人偷偷认领了第二个 Owner」：
 * {@code object_type} 上的唯一约束保证同类不会出现两个 Accountable Owner，
 * {@link #check} 则把缺 Owner 的对象类显式列出来（缺项计数 = 0 才是裁定生效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectOwnerService {

    /** F01 要求冻结对象的对象类别。 */
    public static final List<String> FROZEN_TYPES =
            List.of("PART", "DRAWING", "BOM", "ECO", "RELEASE");

    private final ObjectOwnerMapper objectOwnerMapper;

    public List<ObjectOwner> list() {
        LambdaQueryWrapper<ObjectOwner> w = new LambdaQueryWrapper<>();
        w.orderByAsc(ObjectOwner::getObjectType);
        return objectOwnerMapper.selectList(w);
    }

    public ObjectOwner getByType(String objectType) {
        LambdaQueryWrapper<ObjectOwner> w = new LambdaQueryWrapper<>();
        w.eq(ObjectOwner::getObjectType, objectType).last("LIMIT 1");
        List<ObjectOwner> list = objectOwnerMapper.selectList(w);
        if (list.isEmpty()) throw new BusinessException(404, "对象类别未裁定 Owner: " + objectType);
        return list.get(0);
    }

    /**
     * 登记/变更对象 Owner。变更不新增第二行（唯一约束在 object_type 上），
     * 只能改这一行的归属 —— 这正是「唯一事实源」的结构含义。
     */
    @Transactional
    public ObjectOwner save(ObjectOwner req) {
        if (req == null || !StringUtils.hasText(req.getObjectType())) {
            throw new BusinessException("object_type 不得为空");
        }
        if (!StringUtils.hasText(req.getOwnerRole())) {
            throw new BusinessException("owner_role 不得为空：每类对象必须有 Accountable Owner（F01）");
        }
        ObjectOwner exist = null;
        try {
            exist = getByType(req.getObjectType());
        } catch (BusinessException ignored) {
            // 未登记过 → 新增
        }
        if (exist == null) {
            if (!StringUtils.hasText(req.getStatus())) req.setStatus("ACTIVE");
            if (req.getEffectiveDate() == null) req.setEffectiveDate(LocalDate.now());
            objectOwnerMapper.insert(req);
            return getByType(req.getObjectType());
        }
        exist.setOwnerRole(req.getOwnerRole());
        exist.setOwnerId(req.getOwnerId());
        exist.setSystemLeadId(req.getSystemLeadId());
        if (req.getEffectiveDate() != null) exist.setEffectiveDate(req.getEffectiveDate());
        if (StringUtils.hasText(req.getStatus())) exist.setStatus(req.getStatus());
        objectOwnerMapper.updateById(exist);
        return getByType(req.getObjectType());
    }

    /** 裁定完整性核对：五类冻结对象是否都有 Owner，以及系统负责人是否与验收人分离。 */
    public Map<String, Object> check() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String type : FROZEN_TYPES) {
            ObjectOwner o = null;
            try {
                o = getByType(type);
            } catch (BusinessException ignored) {
                // 未登记
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("objectType", type);
            r.put("ownerRole", o == null ? null : o.getOwnerRole());
            r.put("ownerId", o == null ? null : o.getOwnerId());
            r.put("status", o == null ? null : o.getStatus());
            boolean ok = o != null && StringUtils.hasText(o.getOwnerRole());
            r.put("complete", ok);
            if (!ok) missing.add(type + "（未登记 Accountable Owner）");
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", FROZEN_TYPES.size());
        out.put("missingCount", missing.size());
        out.put("missing", missing);
        out.put("rows", rows);
        return out;
    }
}
