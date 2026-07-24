-- ============================================================
-- 数据认领脚本：将现有无主数据归给第一个注册的用户
-- 用法：
--   1. 注册第一个账号
--   2. 在数据库查询该用户的 id：
--      SELECT id FROM users WHERE email = 'your_email@163.com';
--   3. 将下面的 'USER_ID_HERE' 替换为实际的 userId
--   4. 执行本脚本：mysql -u fundapp -p fund_tracker < migrate_ownership.sql
-- ============================================================

SET @target_user_id = 'USER_ID_HERE';

UPDATE holdings SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE transactions SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE dividend_events SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE dca_plans SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE manual_assets SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE asset_snapshots SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE live_expenses SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';
UPDATE coverage_categories SET user_id = @target_user_id WHERE user_id IS NULL OR user_id = '';

SELECT '认领完成' AS result,
       ROW_COUNT() AS affected_rows;
