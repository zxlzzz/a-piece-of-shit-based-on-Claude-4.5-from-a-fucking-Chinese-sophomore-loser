-- -----------------------------------------------------------
-- 视图1：全局玩家积分排行榜
-- 统计每位玩家的总场次、总得分、平均得分、最高单局得分
-- -----------------------------------------------------------
DROP VIEW IF EXISTS `v_leaderboard`;

CREATE VIEW `v_leaderboard` AS
SELECT
    p.id                                  AS `player_id`,
    p.player_id                           AS `player_uuid`,
    p.name                                AS `player_name`,
    p.username                            AS `username`,
    COUNT(pg.id)                          AS `total_games`,
    COALESCE(SUM(pg.score), 0)            AS `total_score`,
    ROUND(COALESCE(AVG(pg.score), 0), 2)  AS `avg_score`,
    COALESCE(MAX(pg.score), 0)            AS `best_score`,
    MAX(pg.created_at)                    AS `last_played_at`
FROM `players` p
LEFT JOIN `player_game` pg ON pg.player_id = p.id
WHERE p.deleted = 0
GROUP BY p.id, p.player_id, p.name, p.username
ORDER BY `total_score` DESC;


-- -----------------------------------------------------------
-- 视图2：当前活跃房间列表（含在线玩家数）
-- -----------------------------------------------------------
DROP VIEW IF EXISTS `v_active_rooms`;

CREATE VIEW `v_active_rooms` AS
SELECT
    r.id                             AS `room_id`,
    r.room_code                      AS `room_code`,
    r.status                         AS `status`,
    r.max_players                    AS `max_players`,
    r.question_count                 AS `question_count`,
    r.time_limit                     AS `time_limit`,
    r.ranking_mode                   AS `ranking_mode`,
    r.game_mode                      AS `game_mode`,
    r.chat_enabled                   AS `chat_enabled`,
    COUNT(p.id)                      AS `current_players`,
    r.created_at                     AS `created_at`
FROM `rooms` r
LEFT JOIN `players` p ON p.room_id = r.id AND p.deleted = 0
WHERE r.status IN ('WAITING', 'PLAYING')
GROUP BY r.id, r.room_code, r.status, r.max_players, r.question_count,
         r.time_limit, r.ranking_mode, r.game_mode, r.chat_enabled, r.created_at
ORDER BY r.created_at DESC;


-- -----------------------------------------------------------
-- 视图3：题目使用统计总览
-- 整合 question_statistics 与 questions 信息
-- -----------------------------------------------------------
DROP VIEW IF EXISTS `v_question_stats`;

CREATE VIEW `v_question_stats` AS
SELECT
    q.id                                AS `question_id`,
    q.type                              AS `question_type`,
    q.strategy_id                       AS `strategy_id`,
    LEFT(q.text, 60)                    AS `question_preview`,
    q.min_players                       AS `min_players`,
    q.max_players                       AS `max_players`,
    COALESCE(qs.total_plays, 0)         AS `total_plays`,
    qs.last_played_at                   AS `last_played_at`,
    qs.choice_distribution_json         AS `distribution`,
    -- 标签聚合
    GROUP_CONCAT(qt.name ORDER BY qt.name SEPARATOR ', ') AS `tags`
FROM `questions` q
LEFT JOIN `question_statistics` qs   ON qs.question_id = q.id
LEFT JOIN `question_tag_relation` qtr ON qtr.question_id = q.id
LEFT JOIN `question_tag` qt           ON qt.id = qtr.tag_id
GROUP BY q.id, q.type, q.strategy_id, q.text, q.min_players, q.max_players,
         qs.total_plays, qs.last_played_at, qs.choice_distribution_json
ORDER BY `total_plays` DESC;
