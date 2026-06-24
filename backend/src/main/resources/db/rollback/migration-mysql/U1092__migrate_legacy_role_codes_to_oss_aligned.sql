-- Input: V1092__migrate_legacy_role_codes_to_oss_aligned.sql
-- Data rollback required: 回滚 V1092，将新角色码恢复为旧角色码。
-- 当前新 code（source_code）→ 回滚目标旧 code（target_code）映射：
--  - /bidAdmin        → bid_admin          （注意：OSS 投标管理员带前导斜杠）
--  - bid-TeamLeader   → bid_lead
--  - bid-projectLeader → sales
--  - bid-Team         → bid_specialist
--  - bid-administration → admin_staff
--  - bid-otherDept    → bid_other_dept
--  - admin 保持不变
-- 注意：回滚是幂等的，新 code 不存在时 no-op。
--
-- 重要：直接回滚到 V1092 之前的状态，跳过 V1074 的中间态（bidAdmin camelCase）。
--   原始 rollback 分两步（/bidAdmin → bidAdmin → bid_admin）会导致互相吞掉：
--   步骤 0 把 /bidAdmin 改成 bidAdmin 后，步骤 1 的 WHERE code='/bidAdmin' 永远不命中。
--   现在合并为单步：/bidAdmin → bid_admin，避免中间态问题。
--
-- MySQL 兼容性：使用 JOIN 绕过 ERROR 1093，避免在 UPDATE/DELETE roles 时通过子查询引用 roles。
--   使用 tmp_role_mappings 临时表仅存放静态映射关系，不存放运行时状态。
--
-- 事务：使用显式事务保证原子性，中途失败回滚避免半回滚状态

START TRANSACTION;

-- 静态映射表：source_code = 当前新角色码，target_code = 回滚目标旧角色码
DROP TEMPORARY TABLE IF EXISTS tmp_role_mappings;
CREATE TEMPORARY TABLE tmp_role_mappings (
    source_code VARCHAR(100) PRIMARY KEY,
    target_code VARCHAR(100) NOT NULL
);
INSERT INTO tmp_role_mappings (source_code, target_code) VALUES
    ('/bidAdmin', 'bid_admin'),
    ('bid-TeamLeader', 'bid_lead'),
    ('bid-projectLeader', 'sales'),
    ('bid-Team', 'bid_specialist'),
    ('bid-administration', 'admin_staff'),
    ('bid-otherDept', 'bid_other_dept');

-- 步骤 1: 新旧角色同时存在时，合并用户到旧角色
UPDATE users u
JOIN roles r_source ON r_source.id = u.role_id
JOIN tmp_role_mappings m ON m.source_code = r_source.code
JOIN roles r_target ON r_target.code = m.target_code
SET u.role_id = r_target.id;

-- 步骤 2: 新旧角色同时存在时，删除新角色
DELETE r_source
FROM roles r_source
JOIN tmp_role_mappings m ON m.source_code = r_source.code
JOIN roles r_target ON r_target.code = m.target_code;

-- 步骤 3: 旧角色不存在时，直接改名新角色
UPDATE roles r_source
JOIN tmp_role_mappings m ON m.source_code = r_source.code
LEFT JOIN roles r_target ON r_target.code = m.target_code
SET r_source.code = m.target_code,
    r_source.updated_at = NOW()
WHERE r_target.id IS NULL;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS tmp_role_mappings;

COMMIT;
