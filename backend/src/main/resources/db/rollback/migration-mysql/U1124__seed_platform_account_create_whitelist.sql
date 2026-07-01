-- U1124: 回滚平台账户创建白名单种子数据
-- Source: V1124__seed_platform_account_create_whitelist.sql
-- Input: 无
-- Data rollback required: 删除 system_settings 中的白名单配置行

DELETE FROM system_settings WHERE config_key = 'platform_account_create_whitelist';
