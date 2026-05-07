-- -----------------------------------------------------------
-- 存储过程1：统计指定游戏的排名汇总
-- 输入：游戏ID
-- 功能：按 player_game 表中的 score 降序排名，输出该局所有
--       玩家的排名、昵称、得分。
-- -----------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_game_ranking`//

CREATE PROCEDURE `sp_game_ranking`(IN p_game_id BIGINT)
BEGIN
    SELECT
        RANK() OVER (ORDER BY pg.score DESC) AS `rank`,
        p.name                                AS `player_name`,
        p.player_id                           AS `player_uuid`,
        pg.score                              AS `total_score`,
        COUNT(s.id)                           AS `submission_count`
    FROM `player_game` pg
    INNER JOIN `players` p    ON p.id  = pg.player_id
    LEFT  JOIN `submissions` s ON s.game_id = pg.game_id AND s.player_id = pg.player_id
    WHERE pg.game_id = p_game_id
    GROUP BY pg.id, p.name, p.player_id, pg.score
    ORDER BY pg.score DESC;
END//


-- -----------------------------------------------------------
-- 存储过程2：题目使用频次与平均得分统计
-- 功能：汇总所有题目被作答的次数、平均得分、最高分、最低分
-- -----------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_question_usage_stats`//

CREATE PROCEDURE `sp_question_usage_stats`()
BEGIN
    SELECT
        q.id                                AS `question_id`,
        q.strategy_id                       AS `strategy`,
        q.type                              AS `question_type`,
        LEFT(q.text, 40)                    AS `question_preview`,
        COUNT(s.id)                         AS `times_played`,
        ROUND(AVG(s.score_gained), 2)       AS `avg_score`,
        MAX(s.score_gained)                 AS `max_score`,
        MIN(s.score_gained)                 AS `min_score`,
        COUNT(DISTINCT s.game_id)           AS `game_count`
    FROM `questions` q
    LEFT JOIN `submissions` s ON s.question_id = q.id
    GROUP BY q.id, q.strategy_id, q.type, q.text
    ORDER BY `times_played` DESC;
END//


-- -----------------------------------------------------------
-- 存储过程3（加分项）：清理过期空房间
-- 功能：删除超过指定小时数且状态为 WAITING、无玩家的房间
-- -----------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_cleanup_empty_rooms`//

CREATE PROCEDURE `sp_cleanup_empty_rooms`(IN p_hours INT)
BEGIN
    DECLARE v_count INT DEFAULT 0;

    -- 先统计要删除的数量
    SELECT COUNT(*) INTO v_count
    FROM `rooms` r
    WHERE r.status = 'WAITING'
      AND r.created_at < DATE_SUB(NOW(), INTERVAL p_hours HOUR)
      AND NOT EXISTS (
          SELECT 1 FROM `players` p
          WHERE p.room_id = r.id AND p.deleted = 0
      );

    -- 执行删除
    DELETE FROM `rooms`
    WHERE status = 'WAITING'
      AND created_at < DATE_SUB(NOW(), INTERVAL p_hours HOUR)
      AND NOT EXISTS (
          SELECT 1 FROM `players` p
          WHERE p.room_id = rooms.id AND p.deleted = 0
      );

    -- 返回清理结果
    SELECT v_count AS `rooms_cleaned`, NOW() AS `cleaned_at`;
END//

DELIMITER ;
