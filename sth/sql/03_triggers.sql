-- -----------------------------------------------------------
-- 触发器1：提交记录插入后，自动更新题目统计表
-- 当 submissions 表插入新记录时，自动将 question_statistics
-- 中对应题目的 total_plays +1 并更新 last_played_at。
-- 若该题目尚无统计记录则自动插入。
-- -----------------------------------------------------------
DROP TRIGGER IF EXISTS `trg_submission_update_stats`//

CREATE TRIGGER `trg_submission_update_stats`
AFTER INSERT ON `submissions`
FOR EACH ROW
BEGIN
    -- 使用 INSERT ... ON DUPLICATE KEY UPDATE 实现 upsert
    INSERT INTO `question_statistics` (`question_id`, `total_plays`, `last_played_at`, `updated_at`)
    VALUES (NEW.question_id, 1, NOW(), NOW())
    ON DUPLICATE KEY UPDATE
        `total_plays`    = `total_plays` + 1,
        `last_played_at` = NOW(),
        `updated_at`     = NOW();
END//


-- -----------------------------------------------------------
-- 触发器2：房间状态变更为 FINISHED 时，自动更新游戏结束时间
-- 当 rooms 表的 status 从非 FINISHED 更新为 FINISHED 时，
-- 将该房间下所有未设置 end_time 的游戏记录补充结束时间。
-- -----------------------------------------------------------
DROP TRIGGER IF EXISTS `trg_room_finish_update_games`//

CREATE TRIGGER `trg_room_finish_update_games`
AFTER UPDATE ON `rooms`
FOR EACH ROW
BEGIN
    IF OLD.status <> 'FINISHED' AND NEW.status = 'FINISHED' THEN
        UPDATE `games`
        SET    `end_time`   = NOW(),
               `updated_at` = NOW()
        WHERE  `room_id`  = NEW.id
          AND  `end_time`  IS NULL;
    END IF;
END//


-- -----------------------------------------------------------
-- 触发器3（加分项）：玩家软删除时自动记录删除时间
-- 当 players 表 deleted 从 0 改为 1 时，自动填入 deleted_at
-- -----------------------------------------------------------
DROP TRIGGER IF EXISTS `trg_player_soft_delete`//

CREATE TRIGGER `trg_player_soft_delete`
BEFORE UPDATE ON `players`
FOR EACH ROW
BEGIN
    IF OLD.deleted = 0 AND NEW.deleted = 1 THEN
        SET NEW.deleted_at = NOW();
    END IF;
    -- 恢复时清除删除时间
    IF OLD.deleted = 1 AND NEW.deleted = 0 THEN
        SET NEW.deleted_at = NULL;
    END IF;
END//

DELIMITER ;
