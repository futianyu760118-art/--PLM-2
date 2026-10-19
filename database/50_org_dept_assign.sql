-- 50: 研发自治中心 - 我的待办组织架构
-- 按角色映射部门，自动补齐 sys_user.dept_id（仅对当前为空且角色可映射部门的用户）。
-- 角色→部门映射（后续 Java 侧 OrgTodoService 保持一致）：
--   engineer/ENGINEER/rd_manager/project_manager -> RD(研发中心)
--   sales_manager/sales/SALES/sales_director/sales_user -> SALES(销售部)
--   finance/cw1 -> FIN(财务部)  purchase -> SCM(供应链中心)
--   hr_manager -> ADM(人事行政部)  QUALITY -> QA(品质部)
--   ADMIN/admin/gm -> 不映射(全局)
UPDATE sys_user u SET dept_id = (
    SELECT d.id
    FROM sys_user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    JOIN sys_department d ON d.dept_code =
        CASE r.role_code
            WHEN 'engineer'        THEN 'RD'
            WHEN 'ENGINEER'        THEN 'RD'
            WHEN 'rd_manager'      THEN 'RD'
            WHEN 'project_manager' THEN 'RD'
            WHEN 'sales_manager'   THEN 'SALES'
            WHEN 'sales'           THEN 'SALES'
            WHEN 'SALES'           THEN 'SALES'
            WHEN 'sales_director'  THEN 'SALES'
            WHEN 'sales_user'      THEN 'SALES'
            WHEN 'finance'         THEN 'FIN'
            WHEN 'cw1'             THEN 'FIN'
            WHEN 'purchase'        THEN 'SCM'
            WHEN 'hr_manager'      THEN 'ADM'
            WHEN 'QUALITY'         THEN 'QA'
        END
    WHERE ur.user_id = u.id
    ORDER BY r.id
    LIMIT 1
)
WHERE u.dept_id IS NULL
  AND EXISTS (
    SELECT 1 FROM sys_user_role ur2
    JOIN sys_role r2 ON r2.id = ur2.role_id
    WHERE ur2.user_id = u.id
      AND r2.role_code IN ('engineer','ENGINEER','rd_manager','project_manager',
                           'sales_manager','sales','SALES','sales_director','sales_user',
                           'finance','cw1','purchase','hr_manager','QUALITY'));
