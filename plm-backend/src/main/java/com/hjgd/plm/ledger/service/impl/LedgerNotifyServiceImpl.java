package com.hjgd.plm.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ledger.config.LedgerProperties;
import com.hjgd.plm.ledger.entity.PlmNotification;
import com.hjgd.plm.ledger.entity.PlmNotificationSetting;
import com.hjgd.plm.ledger.mapper.PlmNotificationMapper;
import com.hjgd.plm.ledger.mapper.PlmNotificationSettingMapper;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站内通知实现（AC-A4-7 ~ AC-A4-11）。
 *
 * <p>聚合窗口：以 5 分钟为粒度把「当前时刻」向下取整得到窗口起点，
 * 同一窗口内同一用户/类型/对象复用同一行 agg_key，明细追加进 contentJson.changes。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerNotifyServiceImpl implements LedgerNotifyService {

    /** 单条通知保留的最大明细条数，超出丢弃最旧的，避免 JSONB 无限增长 */
    private static final int MAX_CHANGES = 100;

    private final PlmNotificationMapper notificationMapper;
    private final PlmNotificationSettingMapper settingMapper;
    private final LedgerProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void notify(Long userId, String notifyType, String title, String objectType,
                       String objectId, List<Map<String, Object>> changes,
                       Map<String, Object> extra) {
        if (userId == null || !StringUtils.hasText(notifyType)) {
            return;
        }
        if (!enabled(userId, notifyType)) {
            return;
        }
        String aggKey = aggKey(userId, notifyType, objectType, objectId);
        PlmNotification existing = notificationMapper.selectOne(
                new LambdaQueryWrapper<PlmNotification>().eq(PlmNotification::getAggKey, aggKey)
                        .orderByDesc(PlmNotification::getId).last("LIMIT 1"));
        List<Map<String, Object>> merged = new ArrayList<>();
        if (existing != null) {
            merged.addAll(readChanges(existing.getContentJson()));
        }
        if (changes != null) {
            merged.addAll(changes);
        }
        if (merged.size() > MAX_CHANGES) {
            merged = new ArrayList<>(merged.subList(merged.size() - MAX_CHANGES, merged.size()));
        }

        Map<String, Object> content = new LinkedHashMap<>();
        if (extra != null) {
            content.putAll(extra);
        }
        content.put("operator", operator());
        content.put("time", LocalDateTime.now().toString());
        content.put("changes", merged);

        PlmNotification row = existing == null ? new PlmNotification() : existing;
        row.setUserId(userId);
        row.setNotifyType(notifyType);
        row.setTitle(title);
        row.setContentJson(json(content));
        row.setObjectType(objectType);
        row.setObjectId(objectId);
        row.setReadStatus(0);
        row.setAggKey(aggKey);
        if (existing == null) {
            notificationMapper.insert(row);
        } else {
            notificationMapper.updateById(row);
        }
    }

    @Override
    public void notifyAll(List<Long> userIds, String notifyType, String title, String objectType,
                          String objectId, List<Map<String, Object>> changes, Map<String, Object> extra) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> unique = new LinkedHashSet<>(userIds);
        unique.remove(null);
        Long self = currentUserIdOrNull();
        for (Long uid : unique) {
            // 操作人自己不需要收到自己触发的通知
            if (uid.equals(self)) {
                continue;
            }
            try {
                notify(uid, notifyType, title, objectType, objectId, changes, extra);
            } catch (Exception e) {
                // 通知失败不影响主链路（AC-A3-8 记录但不阻断）
                log.warn("通知发送失败 userId={} type={}: {}", uid, notifyType, e.getMessage());
            }
        }
    }

    @Override
    public boolean enabled(Long userId, String notifyType) {
        PlmNotificationSetting setting = settingMapper.selectOne(
                new LambdaQueryWrapper<PlmNotificationSetting>()
                        .eq(PlmNotificationSetting::getUserId, userId)
                        .eq(PlmNotificationSetting::getNotifyType, notifyType)
                        .last("LIMIT 1"));
        // 未配置视为开启（AC-A4-11 默认全开）
        return setting == null || setting.getEnabled() == null || setting.getEnabled() == 1;
    }

    @Override
    @Transactional
    public PlmNotificationSetting setEnabled(Long userId, String notifyType, boolean enabled) {
        if (!StringUtils.hasText(notifyType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "通知类型不能为空");
        }
        PlmNotificationSetting setting = settingMapper.selectOne(
                new LambdaQueryWrapper<PlmNotificationSetting>()
                        .eq(PlmNotificationSetting::getUserId, userId)
                        .eq(PlmNotificationSetting::getNotifyType, notifyType)
                        .last("LIMIT 1"));
        if (setting == null) {
            setting = new PlmNotificationSetting();
            setting.setUserId(userId);
            setting.setNotifyType(notifyType);
            setting.setEnabled(enabled ? 1 : 0);
            settingMapper.insert(setting);
        } else {
            setting.setEnabled(enabled ? 1 : 0);
            settingMapper.updateById(setting);
        }
        return setting;
    }

    @Override
    public List<PlmNotificationSetting> settings(Long userId) {
        List<PlmNotificationSetting> configured = settingMapper.selectList(
                new LambdaQueryWrapper<PlmNotificationSetting>()
                        .eq(PlmNotificationSetting::getUserId, userId));
        Map<String, PlmNotificationSetting> byType = new LinkedHashMap<>();
        for (PlmNotificationSetting s : configured) {
            byType.put(s.getNotifyType(), s);
        }
        // 补齐未配置的类型，前端开关列表恒定展示全部类型
        List<PlmNotificationSetting> out = new ArrayList<>();
        for (String type : allTypes()) {
            PlmNotificationSetting s = byType.get(type);
            if (s == null) {
                s = new PlmNotificationSetting();
                s.setUserId(userId);
                s.setNotifyType(type);
                s.setEnabled(1);
            }
            out.add(s);
        }
        return out;
    }

    @Override
    public PageResult<PlmNotification> page(Long userId, Integer readStatus, String notifyType,
                                            int pageNum, int pageSize) {
        LambdaQueryWrapper<PlmNotification> w = new LambdaQueryWrapper<PlmNotification>()
                .eq(PlmNotification::getUserId, userId)
                .eq(readStatus != null, PlmNotification::getReadStatus, readStatus)
                .eq(StringUtils.hasText(notifyType), PlmNotification::getNotifyType, notifyType)
                .orderByDesc(PlmNotification::getId);
        Page<PlmNotification> p = notificationMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<PlmNotification>()
                .eq(PlmNotification::getUserId, userId)
                .eq(PlmNotification::getReadStatus, 0));
    }

    @Override
    @Transactional
    public void markRead(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        PlmNotification patch = new PlmNotification();
        patch.setReadStatus(1);
        notificationMapper.update(patch, new LambdaQueryWrapper<PlmNotification>()
                .eq(PlmNotification::getUserId, userId)
                .in(PlmNotification::getId, ids));
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        PlmNotification patch = new PlmNotification();
        patch.setReadStatus(1);
        notificationMapper.update(patch, new LambdaQueryWrapper<PlmNotification>()
                .eq(PlmNotification::getUserId, userId)
                .eq(PlmNotification::getReadStatus, 0));
    }

    @Override
    public Map<String, Object> detail(Long userId, Long id) {
        PlmNotification n = notificationMapper.selectById(id);
        if (n == null || !userId.equals(n.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("notification", n);
        out.put("content", parseJson(n.getContentJson()));
        return out;
    }

    private List<String> allTypes() {
        return List.of(TYPE_LEDGER_CREATED, TYPE_LEDGER_CHANGED, TYPE_LEDGER_DELETED,
                TYPE_LEDGER_LINK, TYPE_OBJ_INVALID, TYPE_SUB_ITEM, TYPE_SUB_REPORT);
    }

    /** agg_key = 用户|类型|对象类型|对象键|窗口起点（AC-A4-10） */
    private String aggKey(Long userId, String notifyType, String objectType, String objectId) {
        long windowMs = Math.max(1, properties.getNotifyAggregateMinutes()) * 60_000L;
        long bucket = (System.currentTimeMillis() / windowMs) * windowMs;
        return userId + "|" + notifyType + "|" + nz(objectType) + "|" + nz(objectId) + "|" + bucket;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readChanges(String contentJson) {
        Object parsed = parseJson(contentJson);
        if (parsed instanceof Map<?, ?> map && map.get("changes") instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private Object parseJson(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return objectMapper.readValue(s, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String operator() {
        try {
            return SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
