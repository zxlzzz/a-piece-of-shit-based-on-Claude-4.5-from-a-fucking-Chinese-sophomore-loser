# Boilune 数据库设计文档（SQL 脚本集）

## 文件清单

| 文件 | 内容 | 课程要求对应 |
|------|------|-------------|
| `01_schema.sql` | 15张表的完整建表语句（含主键、外键、注释） | 数据库结构、完整性约束、SQL建表 |
| `02_indexes.sql` | 全部索引（外键索引 + 业务索引） | 建立合适的索引 |
| `03_triggers.sql` | 3个触发器 | 创建触发器，实现状态自动修改 |
| `04_procedures.sql` | 3个存储过程 | 创建存储过程统计数据 |
| `05_views.sql` | 3个视图 | 创建视图查询信息 |
| `06_sample_data.sql` | 测试数据（12玩家、23题、8房间、7局游戏） | 大量数据导入 |
| `07_er_diagram.md` | Mermaid 语法 E-R 图 + 子系统说明 | 概念结构设计 |

## 执行顺序

```bash
mysql -u root -p boilune < 01_schema.sql
mysql -u root -p boilune < 02_indexes.sql
mysql -u root -p boilune < 03_triggers.sql
mysql -u root -p boilune < 04_procedures.sql
mysql -u root -p boilune < 05_views.sql
mysql -u root -p boilune < 06_sample_data.sql
```

## 表总览（15张）

核心业务：`players`, `rooms`, `games`, `player_game`, `submissions`, `game_results`
题目系统：`questions`, `choice_question_config`, `bid_question_config`, `question_metadata`, `question_tag`, `question_tag_relation`, `question_dependencies`
数据分析：`choice_records`, `question_statistics`


# 注
项目运行未使用这些sql文件，实际使用@Entity，@Table等注解帮助自动构建项目，具体可看backend\src\main\java\org\example\entity和backend\src\main\java\org\example\repository，理论相同，只是简化手动操作