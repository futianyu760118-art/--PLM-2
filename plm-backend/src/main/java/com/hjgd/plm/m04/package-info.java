/**
 * M04 研发数据中台 —— PLM2 的独立业务模块。
 *
 * <p>模块边界（其余包不得反向依赖本模块，本模块只依赖 common / system / project 等平台能力）：
 * <ul>
 *   <li>{@code m04.codegen}    编码规则引擎：{@code plm_code_rule} + {@code plm_code_rule_segment} 驱动，
 *       12 个在用 seq_key 的编号全部由它产出；{@code ProjectCodeGenerator} 负责项目号（客户码三级结构）。</li>
 *   <li>{@code m04.document}   文档中心：五区状态机（WORKING/RELEASED/ARCHIVED/OBSOLETE/RECYCLE）、
 *       版本链、检入检出、权限点。</li>
 *   <li>{@code m04.approval}   审批引擎：flow / node / instance / task 四表，待办与流水共用 task 表
 *       （{@code action IS NULL} 即待办），超时由 {@code ApprovalTimeoutJob} 扫描。</li>
 *   <li>{@code m04.ecr}        变更申请 ECR：提交即取号并启动 {@code ECR_DEFAULT} 流程，通过后可一键转 ECN。</li>
 * </ul>
 *
 * <p>数据库脚本：{@code database/59_m04_core_gap.sql} ~ {@code 66_m04_approval_flow_seed.sql}，
 * 必须按编号顺序执行；详见 {@code docs/m04/README.md}。
 *
 * <p>对外 HTTP 入口：{@code /v1/codegen}、{@code /v1/coderule}、{@code /document}、{@code /approval}、{@code /ecr}。
 */
package com.hjgd.plm.m04;
