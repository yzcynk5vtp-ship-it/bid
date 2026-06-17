-- CO-XXX: 删除 task_executor（任务执行人）角色
-- 该角色功能已由 bid_specialist 覆盖，不再需要独立角色
DELETE FROM role_menu_permissions WHERE role_code = 'task_executor';
DELETE FROM roles WHERE code = 'task_executor';
