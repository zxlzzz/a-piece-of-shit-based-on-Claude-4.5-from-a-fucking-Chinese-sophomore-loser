SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------
-- 1. 玩家表 (players)
-- 对应 PlayerEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `players`;
CREATE TABLE `players` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `player_id`  VARCHAR(50)  NOT NULL COMMENT '游戏逻辑用UUID',
    `username`   VARCHAR(50)  NULL     COMMENT '登录用户名（唯一，游客为空）',
    `password`   VARCHAR(255) NULL     COMMENT 'BCrypt加密密码（游客为空）',
    `name`       VARCHAR(50)  NOT NULL COMMENT '游戏昵称（可重复）',
    `ready`      TINYINT(1)   NULL     COMMENT '是否准备',
    `spectator`  TINYINT(1)   NULL     COMMENT '是否观战模式',
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `deleted_at` DATETIME     NULL     COMMENT '删除时间',
    `room_id`    BIGINT       NULL     COMMENT '当前所在房间ID',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_id` (`player_id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家表';


-- -----------------------------------------------------------
-- 2. 房间表 (rooms)
-- 对应 RoomEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `rooms`;
CREATE TABLE `rooms` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_code`             VARCHAR(20)  NOT NULL COMMENT '房间码（唯一标识）',
    `status`                VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT '房间状态: WAITING/PLAYING/FINISHED',
    `max_players`           INT          NOT NULL COMMENT '最大玩家数',
    `question_count`        INT          NOT NULL COMMENT '题目数量',
    `time_limit`            INT          NULL     DEFAULT 30 COMMENT '每题时长限制（秒）',
    `password`              VARCHAR(50)  NULL     COMMENT '房间密码（可选）',
    `host_player_id`        VARCHAR(50)  NULL     COMMENT '房主玩家ID',
    `ranking_mode`          VARCHAR(20)  NULL     DEFAULT 'standard' COMMENT '排名模式: standard/closest_to_avg/closest_to_target',
    `target_score`          INT          NULL     COMMENT '目标分数（closest_to_target时有效）',
    `win_conditions_json`   TEXT         NULL     COMMENT '通关条件JSON',
    `question_tag_ids_json` TEXT         NULL     COMMENT '题目标签筛选JSON',
    `game_mode`             VARCHAR(20)  NOT NULL DEFAULT 'SYNCHRONIZED' COMMENT '游戏模式: SYNCHRONIZED/ASYNC',
    `chat_enabled`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用聊天',
    `private_chat_enabled`  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用私聊',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_code` (`room_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏房间表';

-- players.room_id 外键（房间表建完后添加）
ALTER TABLE `players` ADD CONSTRAINT `fk_players_room`
    FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE SET NULL;


-- -----------------------------------------------------------
-- 3. 题目表 (questions)
-- 对应 QuestionEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `questions`;
CREATE TABLE `questions` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `type`            VARCHAR(20)  NOT NULL COMMENT '题目类型: CHOICE/BID',
    `text`            TEXT         NOT NULL COMMENT '题目文本',
    `calculate_rule`  TEXT         NULL     COMMENT '计分规则说明',
    `strategy_id`     VARCHAR(100) NOT NULL COMMENT '策略类ID（对应后端Strategy）',
    `min_players`     INT          NULL     COMMENT '最少玩家数',
    `max_players`     INT          NULL     COMMENT '最多玩家数',
    `default_choice`  VARCHAR(50)  NULL     COMMENT '默认选项',
    `has_metadata`    TINYINT(1)   NULL     DEFAULT 0 COMMENT '是否有元数据',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';


-- -----------------------------------------------------------
-- 4. 选择题配置表 (choice_question_config)
-- 对应 ChoiceQuestionConfig.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `choice_question_config`;
CREATE TABLE `choice_question_config` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id`  BIGINT   NOT NULL COMMENT '关联题目ID',
    `options_json` TEXT     NOT NULL COMMENT '选项JSON: [{"key":"A","text":"..."},...]',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_id` (`question_id`),
    CONSTRAINT `fk_choice_config_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选择题配置表';


-- -----------------------------------------------------------
-- 5. 竞价题配置表 (bid_question_config)
-- 对应 BidQuestionConfig.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `bid_question_config`;
CREATE TABLE `bid_question_config` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id` BIGINT   NOT NULL COMMENT '关联题目ID',
    `min_value`   INT      NOT NULL COMMENT '最小竞价值',
    `max_value`   INT      NOT NULL COMMENT '最大竞价值',
    `step`        INT      NULL     COMMENT '步长（可选）',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_id` (`question_id`),
    CONSTRAINT `fk_bid_config_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞价题配置表';


-- -----------------------------------------------------------
-- 6. 题目元数据表 (question_metadata)
-- 对应 QuestionMetadata.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `question_metadata`;
CREATE TABLE `question_metadata` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id`          BIGINT       NOT NULL COMMENT '关联题目ID',
    `sequence_group_id`    VARCHAR(100) NULL     COMMENT '序列组ID',
    `sequence_order`       INT          NULL     COMMENT '序列中的顺序',
    `total_sequence_count` INT          NULL     COMMENT '该序列共几题',
    `is_repeatable`        TINYINT(1)   NULL     COMMENT '是否可重复',
    `repeat_times`         INT          NULL     COMMENT '重复次数',
    `repeat_interval`      INT          NULL     COMMENT '重复间隔',
    `repeat_group_id`      VARCHAR(100) NULL     COMMENT '重复组ID',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_id` (`question_id`),
    CONSTRAINT `fk_metadata_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目元数据（重复/序列配置）';


-- -----------------------------------------------------------
-- 7. 标签表 (question_tag)
-- 对应 QuestionTagEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `question_tag`;
CREATE TABLE `question_tag` (
    `id`       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`     VARCHAR(50) NOT NULL COMMENT '标签名称',
    `category` VARCHAR(20) NOT NULL COMMENT '标签分类: mechanism/strategy',
    `color`    VARCHAR(20) NULL     COMMENT '显示颜色',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签表';


-- -----------------------------------------------------------
-- 8. 题目-标签关联表 (question_tag_relation)
-- 对应 QuestionTagRelationEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `question_tag_relation`;
CREATE TABLE `question_tag_relation` (
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `tag_id`      BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`question_id`, `tag_id`),
    CONSTRAINT `fk_tagrel_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tagrel_tag`      FOREIGN KEY (`tag_id`)      REFERENCES `question_tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目-标签多对多关联表';


-- -----------------------------------------------------------
-- 9. 游戏表 (games)
-- 对应 GameEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `games`;
CREATE TABLE `games` (
    `id`         BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id`    BIGINT     NOT NULL COMMENT '所属房间ID',
    `start_time` DATETIME   NULL     COMMENT '游戏开始时间',
    `end_time`   DATETIME   NULL     COMMENT '游戏结束时间',
    `is_test`    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为测试游戏（虚拟玩家）',
    `created_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_games_room` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏场次表';


-- -----------------------------------------------------------
-- 10. 玩家-游戏关联表 (player_game)
-- 对应 PlayerGameEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `player_game`;
CREATE TABLE `player_game` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `player_id`  BIGINT   NOT NULL COMMENT '玩家ID',
    `game_id`    BIGINT   NOT NULL COMMENT '游戏ID',
    `score`      INT      NOT NULL DEFAULT 0 COMMENT '该局总得分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_pg_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pg_game`   FOREIGN KEY (`game_id`)   REFERENCES `games` (`id`)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家-游戏参与记录（含得分）';


-- -----------------------------------------------------------
-- 11. 提交记录表 (submissions)
-- 对应 SubmissionEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `submissions`;
CREATE TABLE `submissions` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id`  BIGINT      NOT NULL COMMENT '题目ID',
    `player_id`    BIGINT      NOT NULL COMMENT '玩家ID',
    `game_id`      BIGINT      NOT NULL COMMENT '游戏ID',
    `choice`       VARCHAR(50) NOT NULL COMMENT '玩家选择',
    `score_gained` INT         NULL     COMMENT '本题得分',
    `submitted_at` DATETIME    NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_sub_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`),
    CONSTRAINT `fk_sub_player`   FOREIGN KEY (`player_id`)   REFERENCES `players` (`id`)  ON DELETE CASCADE,
    CONSTRAINT `fk_sub_game`     FOREIGN KEY (`game_id`)     REFERENCES `games` (`id`)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家答题提交记录';


-- -----------------------------------------------------------
-- 12. 游戏结果表 (game_results)
-- 对应 GameResultEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `game_results`;
CREATE TABLE `game_results` (
    `id`                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id`              BIGINT   NOT NULL COMMENT '房间ID',
    `game_id`              BIGINT   NOT NULL COMMENT '游戏ID（唯一）',
    `question_count`       INT      NOT NULL COMMENT '题目数量',
    `player_count`         INT      NOT NULL COMMENT '玩家数量',
    `leaderboard_json`     TEXT     NULL     COMMENT '排行榜JSON',
    `question_details_json` TEXT    NULL     COMMENT '题目详情JSON',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_id` (`game_id`),
    CONSTRAINT `fk_result_room` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`),
    CONSTRAINT `fk_result_game` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏结果（排行榜与题目详情）';


-- -----------------------------------------------------------
-- 13. 选项记录表 (choice_records)
-- 对应 ChoiceRecordEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `choice_records`;
CREATE TABLE `choice_records` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id`  BIGINT      NOT NULL COMMENT '题目ID',
    `player_id`    BIGINT      NULL     COMMENT '玩家ID（Bot/匿名可为空）',
    `choice`       VARCHAR(10) NOT NULL COMMENT '选择的选项',
    `player_count` INT         NOT NULL COMMENT '当局玩家人数',
    `game_type`    VARCHAR(20) NOT NULL COMMENT '游戏类型: MATCH/PRACTICE',
    `room_code`    VARCHAR(10) NULL     COMMENT '房间代码（追踪用）',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_cr_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`),
    CONSTRAINT `fk_cr_player`   FOREIGN KEY (`player_id`)   REFERENCES `players` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选项记录（用于统计分析和Bot策略）';


-- -----------------------------------------------------------
-- 14. 题目统计表 (question_statistics)
-- 对应 QuestionStatisticsEntity.java
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `question_statistics`;
CREATE TABLE `question_statistics` (
    `id`                      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id`             BIGINT   NOT NULL COMMENT '题目ID',
    `choice_distribution_json` TEXT    NULL     COMMENT '选项分布JSON（按人数区分）',
    `total_plays`             INT      NOT NULL DEFAULT 0 COMMENT '总被玩次数',
    `last_played_at`          DATETIME NULL     COMMENT '最后被玩时间',
    `updated_at`              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_id` (`question_id`),
    CONSTRAINT `fk_stats_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目统计数据（聚合）';


-- -----------------------------------------------------------
-- 15. 题目依赖关系表 (question_dependencies)
-- 对应 QuestionDependency.java
-- 注：虽然前面清单没列，但这是一个有外键的关联表，
--     对文档E-R图有加分，保留。
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `question_dependencies`;
CREATE TABLE `question_dependencies` (
    `question_id`              BIGINT NOT NULL COMMENT '当前题目ID',
    `prerequisite_question_id` BIGINT NOT NULL COMMENT '前置题目ID',
    PRIMARY KEY (`question_id`, `prerequisite_question_id`),
    CONSTRAINT `fk_dep_question` FOREIGN KEY (`question_id`)              REFERENCES `questions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dep_prereq`   FOREIGN KEY (`prerequisite_question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目依赖关系（前置题）';


SET FOREIGN_KEY_CHECKS = 1;
