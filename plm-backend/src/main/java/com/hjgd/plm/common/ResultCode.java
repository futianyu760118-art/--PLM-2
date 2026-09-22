package com.hjgd.plm.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(400, "参数校验错误"),

    PART_NO_DUPLICATE(10001, "物料料号已存在,禁止重复"),
    MATERIAL_NOT_RELEASED(10002, "物料未正式发布,禁止操作"),
    MATERIAL_LOCKED(10003, "物料已发布,基础信息禁止直接修改,请走ECN变更"),
    MATERIAL_STATUS_ERROR(10004, "物料状态不允许此操作"),

    ECN_NOT_APPROVED(11001, "ECN单据未审批通过,版本无法生效"),
    ECN_STATUS_ERROR(11002, "ECN单据状态不允许此操作"),
    ECN_REJECTED(11003, "ECN已被驳回,请修改后重新提交"),
    ECN_NO_PERMISSION(11004, "无当前节点审批权限"),

    BOM_CYCLE_ERROR(12001, "BOM存在循环引用"),
    BOM_ITEM_NOT_FOUND(12002, "BOM明细不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
