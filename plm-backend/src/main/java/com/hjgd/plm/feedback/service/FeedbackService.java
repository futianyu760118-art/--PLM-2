package com.hjgd.plm.feedback.service;

import java.util.List;
import java.util.Map;

/**
 * 问题反馈服务（对齐中台 SPC 反馈模块，含 AI 自动实现）。
 */
public interface FeedbackService {

    Map<String, Object> page(int pageNum, int pageSize, String type, String severity,
                             String status, String aiStatus, String keyword,
                             String sortBy, String sortOrder, String submitterName);

    Map<String, Object> get(Long id);

    Map<String, Object> create(Map<String, Object> body);

    Map<String, Object> update(Long id, Map<String, Object> body);

    Map<String, Object> reply(Long id, String reply, String status);

    Map<String, Object> changeStatus(Long id, String status);

    Map<String, Object> aiQueue(Long id);

    Map<String, Object> aiCancel(Long id);

    Map<String, Object> aiClaim(Long id);

    Map<String, Object> aiProgress(Long id, String stage, List<String> actions);

    Map<String, Object> aiFinish(Long id, boolean success, String branch, String summary,
                                 String error, Map<String, Object> result);

    List<Long> aiQueueWait(int timeoutSeconds);

    void delete(Long id);
}
