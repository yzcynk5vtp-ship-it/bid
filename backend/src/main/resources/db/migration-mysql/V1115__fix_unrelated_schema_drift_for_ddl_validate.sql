-- V1115: 修复无关表 schema 漂移，使 ddl-auto=validate 能在测试中通过
--
-- 背景：
-- - PlatformAccountBorrowServiceMysqlIntegrationTest 此前用 ddl-auto=none 覆盖，
--   绕过无关表的 schema 漂移（被测表已由 V1113 修复）。
-- - 本迁移补齐实体已定义、但 Flyway 链中缺失的列，使 profile 默认的 validate 能通过。
-- - 与 V1114（bid_document_review 建表）配合，可移除测试类上的 ddl-auto=none 覆盖。
--
-- 漂移清单（由 schema-diff 扫描 + Hibernate validate 顺序确认）：
--   1. business_qualifications.retired     —— 实体 BusinessQualificationEntity.retired
--   2. tenders.crm_opportunity_name        —— 实体 Tender.crmOpportunityName
--   3. tenders.project_id                  —— 实体 Tender.projectId
--
-- 风险评估：
-- - 三列在生产 dev 环境中由 ddl-auto=update 隐式创建过，本迁移只是补入 Flyway 链。
-- - retired 为 NOT NULL DEFAULT FALSE，已有行自动填充 false，语义与实体默认值一致。
-- - crm_opportunity_name / project_id 均可空，已有行填充 NULL，无数据风险。
-- - MySQL 8.0 INSTANT 元数据操作，不锁表。
--
-- 幂等设计（第 20 次部署前修复）：
-- - 第 20 次部署 Flyway 预检发现 3 列已由 ddl-auto=update 隐式创建，
--   直接 ADD COLUMN 会报 Duplicate column name。
-- - 改用存储过程 + information_schema 检查，列存在时跳过 ADD COLUMN。
-- - 参考 skill xiyu-server-deploy §7 幂等迁移设计推荐模式。

-- 1. business_qualifications.retired
-- 实体：@Column(nullable = false) private boolean retired;
-- 语义：资质是否已下架（true=已下架）。与 retire_reason 配合使用（V1063）。
DROP PROCEDURE IF EXISTS repair_v1115_col_1;
DELIMITER $$
CREATE PROCEDURE repair_v1115_col_1() BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE()
      AND table_name='business_qualifications'
      AND column_name='retired'
  ) THEN
    ALTER TABLE business_qualifications
        ADD COLUMN retired TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已下架（true=已下架）';
  END IF;
END$$
DELIMITER ;
CALL repair_v1115_col_1();
DROP PROCEDURE IF EXISTS repair_v1115_col_1;

-- 2. tenders.crm_opportunity_name
-- 实体：@Column(name = "crm_opportunity_name", length = 200) private String crmOpportunityName;
-- 语义：关联的 CRM 商机名称（项目负责人触发商机关联后写入）。
DROP PROCEDURE IF EXISTS repair_v1115_col_2;
DELIMITER $$
CREATE PROCEDURE repair_v1115_col_2() BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE()
      AND table_name='tenders'
      AND column_name='crm_opportunity_name'
  ) THEN
    ALTER TABLE tenders
        ADD COLUMN crm_opportunity_name VARCHAR(200) DEFAULT NULL COMMENT '关联的CRM商机名称';
  END IF;
END$$
DELIMITER ;
CALL repair_v1115_col_2();
DROP PROCEDURE IF EXISTS repair_v1115_col_2;

-- 3. tenders.project_id
-- 实体：@Column(name = "project_id") private Long projectId;
-- 语义：关联的投标项目ID（投标立项后写入，项目状态变更时回填标讯状态）。
DROP PROCEDURE IF EXISTS repair_v1115_col_3;
DELIMITER $$
CREATE PROCEDURE repair_v1115_col_3() BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE()
      AND table_name='tenders'
      AND column_name='project_id'
  ) THEN
    ALTER TABLE tenders
        ADD COLUMN project_id BIGINT DEFAULT NULL COMMENT '关联的投标项目ID';
  END IF;
END$$
DELIMITER ;
CALL repair_v1115_col_3();
DROP PROCEDURE IF EXISTS repair_v1115_col_3;
