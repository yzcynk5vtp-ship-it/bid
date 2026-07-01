-- V1124: 种子平台账户创建白名单（system_settings）
-- 允许特定投标专员创建平台账户，payload 为 JSON 数组格式: ["username1", "username2"]

INSERT INTO system_settings (config_key, payload_json, created_at, updated_at)
VALUES ('platform_account_create_whitelist', '["00444"]', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), updated_at = NOW(6);
