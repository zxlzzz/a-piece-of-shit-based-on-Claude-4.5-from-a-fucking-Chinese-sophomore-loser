-- ---------- players ----------
CREATE INDEX `idx_players_room_id`  ON `players` (`room_id`);
CREATE INDEX `idx_players_deleted`  ON `players` (`deleted`);

-- ---------- rooms ----------
CREATE INDEX `idx_rooms_status`     ON `rooms` (`status`);
CREATE INDEX `idx_rooms_game_mode`  ON `rooms` (`game_mode`);
CREATE INDEX `idx_rooms_created_at` ON `rooms` (`created_at`);

-- ---------- games ----------
CREATE INDEX `idx_games_room_id`    ON `games` (`room_id`);
CREATE INDEX `idx_games_is_test`    ON `games` (`is_test`);

-- ---------- submissions（Entity中已定义，此处显式声明） ----------
CREATE INDEX `idx_sub_game_question` ON `submissions` (`game_id`, `question_id`);
CREATE INDEX `idx_sub_player`        ON `submissions` (`player_id`);
CREATE INDEX `idx_sub_question`      ON `submissions` (`question_id`);

-- ---------- player_game ----------
CREATE INDEX `idx_pg_player` ON `player_game` (`player_id`);
CREATE INDEX `idx_pg_game`   ON `player_game` (`game_id`);

-- ---------- game_results ----------
CREATE INDEX `idx_result_room` ON `game_results` (`room_id`);

-- ---------- choice_records（Entity中已定义） ----------
CREATE INDEX `idx_cr_question_id`  ON `choice_records` (`question_id`);
CREATE INDEX `idx_cr_created_at`   ON `choice_records` (`created_at`);
CREATE INDEX `idx_cr_player_count` ON `choice_records` (`player_count`);

-- ---------- question_statistics ----------
-- uk_question_id 已在建表时创建（UNIQUE KEY）

-- ---------- question_metadata ----------
CREATE INDEX `idx_meta_seq_group` ON `question_metadata` (`sequence_group_id`);
CREATE INDEX `idx_meta_rpt_group` ON `question_metadata` (`repeat_group_id`);
