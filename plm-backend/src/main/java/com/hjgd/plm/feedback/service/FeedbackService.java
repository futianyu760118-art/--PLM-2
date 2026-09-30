package com.hjgd.plm.feedback.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.feedback.dto.FeedbackQueryDTO;
import com.hjgd.plm.feedback.dto.FeedbackReceiptVO;
import com.hjgd.plm.feedback.dto.FeedbackSubmitDTO;
import com.hjgd.plm.feedback.dto.FeedbackTransitionDTO;
import com.hjgd.plm.feedback.dto.FeedbackVO;

public interface FeedbackService {

    /** 唯一提交入口:校验 → 去重 → 编号 → 落库,返回回执 */
    FeedbackReceiptVO submit(FeedbackSubmitDTO dto);

    /** 凭反馈编号查询状态(本人 或 总控) */
    FeedbackVO getByNo(String feedbackNo);

    /** 总控分页查询 */
    PageResult<FeedbackVO> page(FeedbackQueryDTO query);

    /** 总控状态流转(待分析→评估中→已立项/已退回) */
    void transition(Long id, FeedbackTransitionDTO dto);
}
