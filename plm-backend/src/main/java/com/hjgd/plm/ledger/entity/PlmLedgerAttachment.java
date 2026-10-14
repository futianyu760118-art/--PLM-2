package com.hjgd.plm.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 台账附件：只存 plm_file 引用，不复制文件本体。
 *
 * <p>一条文件可被多处引用（沿用 evidence 模块「一份文件多处引用」原则）。
 */
@Data
@TableName("plm_ledger_attachment")
public class PlmLedgerAttachment extends BaseEntity {

    private Long ledgerId;
    /** -> plm_file.id */
    private Long fileId;
    /** 冗余文件名/扩展名/大小，列表展示免联查 */
    private String fileName;
    private String fileExt;
    private Long fileSize;
    private Integer sortOrder;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
