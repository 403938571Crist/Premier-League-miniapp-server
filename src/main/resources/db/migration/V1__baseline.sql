-- ============================================================================
-- Flyway V1 baseline — 2026-04-24 生成自 live `premierleague` DB (mysqldump)
-- ============================================================================
-- 这是 Flyway schema 管理的起点。已有 DB（本地 / CloudBase 上已部署过 ddl-auto=update
-- 的实例）由 `spring.flyway.baseline-on-migrate=true` + `baseline-version=1` 自动
-- 标记为 "V1 applied"，不会重跑本文件；全新 DB 会直接执行本文件。
--
-- 以后改表结构的唯一方式：新增 V2__xxx.sql、V3__xxx.sql。禁止手动改本文件。
-- ============================================================================

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_cache` (
  `cache_key` varchar(255) NOT NULL,
  `cache_value` longtext NOT NULL,
  `created_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `hit_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`cache_key`),
  KEY `idx_api_cache_expires` (`expires_at`),
  KEY `idx_api_cache_prefix` (`cache_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_provider` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `display_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_open_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `guest` bit(1) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4qxyf1gjjayd8gpthm7xfkq29` (`external_open_id`),
  KEY `idx_app_user_display_name` (`display_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fetch_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `duplicate_count` int DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `failed_count` int DEFAULT NULL,
  `frequency_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `new_count` int DEFAULT NULL,
  `request_count` int DEFAULT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_count` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_source_type` (`source_type`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=5542 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followed_teams` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_followed_team_user_team` (`user_id`,`team_id`),
  KEY `idx_followed_team_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `matches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_id` bigint DEFAULT NULL COMMENT 'football-data.org API ID',
  `season` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '赛季',
  `matchday` int DEFAULT NULL COMMENT '轮次',
  `match_date` datetime DEFAULT NULL COMMENT '比赛时间',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态: SCHEDULED/LIVE/IN_PLAY/FINISHED',
  `home_team_id` bigint DEFAULT NULL COMMENT '主队ID',
  `home_team_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `home_team_chinese_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `home_team_crest` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `away_team_id` bigint DEFAULT NULL COMMENT '客队ID',
  `away_team_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `away_team_chinese_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `away_team_crest` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `home_score` int DEFAULT NULL COMMENT '主队比分',
  `away_score` int DEFAULT NULL COMMENT '客队比分',
  `home_half_score` int DEFAULT NULL COMMENT '主队半场比分',
  `away_half_score` int DEFAULT NULL COMMENT '客队半场比分',
  `duration` int DEFAULT NULL COMMENT '比赛时长(分钟)',
  `stage` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '阶段: REGULAR_TIME/EXTRA_TIME/PENALTIES',
  `venue` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '比赛场地',
  `referee` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '裁判',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `api_id` (`api_id`),
  KEY `idx_match_date` (`match_date`),
  KEY `idx_status` (`status`),
  KEY `idx_matchday` (`matchday`),
  KEY `idx_home_team` (`home_team_id`),
  KEY `idx_away_team` (`away_team_id`),
  KEY `idx_api_id` (`api_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='英超赛程/比赛表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '使用指纹作为ID',
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `summary` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '摘要',
  `source` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源名称',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型',
  `media_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '媒体类型',
  `source_published_at` datetime NOT NULL COMMENT '源站发布时间',
  `author` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作者',
  `cover_image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面图URL',
  `tags` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `related_team_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联球队ID',
  `related_player_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联球员ID',
  `hot_score` int DEFAULT '0' COMMENT '热度值',
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原文链接',
  `source_note` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源说明',
  `content` text COLLATE utf8mb4_unicode_ci,
  `fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '去重指纹',
  `fetched_at` datetime DEFAULT NULL COMMENT '抓取时间',
  `content_updated_at` datetime DEFAULT NULL COMMENT '内容更新时间',
  `fetch_batch_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抓取批次号',
  `fetch_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'success' COMMENT '抓取状态',
  `fetch_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抓取失败原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `fingerprint` (`fingerprint`),
  UNIQUE KEY `idx_fingerprint` (`fingerprint`),
  KEY `idx_source_type` (`source_type`),
  KEY `idx_media_type` (`media_type`),
  KEY `idx_source_published_at` (`source_published_at`),
  KEY `idx_hot_score` (`hot_score`),
  KEY `idx_published_at` (`source_published_at`),
  KEY `idx_fetched_at` (`fetched_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `player_social` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `handle` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_active_at` datetime(6) DEFAULT NULL,
  `platform` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `player_id` bigint DEFAULT NULL,
  `player_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `team_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `verified` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_player_id` (`player_id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_platform` (`platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `players` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_id` bigint DEFAULT NULL COMMENT 'football-data.org API ID',
  `team_id` bigint DEFAULT NULL COMMENT '所属球队ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `chinese_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '中文名',
  `photo_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `shirt_number` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '球衣号码',
  `position` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '位置: Goalkeeper/Defender/Midfielder/Attacker',
  `chinese_position` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '中文位置',
  `nationality` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '国籍',
  `date_of_birth` date DEFAULT NULL COMMENT '出生日期',
  `height` int DEFAULT NULL COMMENT '身高(cm)',
  `weight` int DEFAULT NULL COMMENT '体重(kg)',
  `foot` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '惯用脚: Left/Right',
  `contract_until` date DEFAULT NULL COMMENT '合同到期日',
  `market_value` bigint DEFAULT NULL COMMENT '市场价值(欧元)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `api_id` (`api_id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_position` (`position`),
  KEY `idx_api_id` (`api_id`)
) ENGINE=InnoDB AUTO_INCREMENT=673 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='球员表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sync_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `detail_log` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `error_message` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fail_count` int DEFAULT NULL,
  `items_count` int DEFAULT NULL,
  `source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `success_count` int DEFAULT NULL,
  `sync_time` datetime(6) NOT NULL,
  `sync_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sync_type` (`sync_type`),
  KEY `idx_sync_time` (`sync_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5250 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teams` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_id` bigint DEFAULT NULL COMMENT 'football-data.org API ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '球队全称',
  `short_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '简称',
  `chinese_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '中文名称',
  `crest_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '队徽URL',
  `venue` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主场',
  `founded` int DEFAULT NULL COMMENT '成立年份',
  `club_colors` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '俱乐部颜色',
  `website` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '官方网站',
  `position` int DEFAULT NULL COMMENT '排名',
  `played_games` int DEFAULT '0' COMMENT '已赛场次',
  `won` int DEFAULT '0' COMMENT '胜',
  `draw` int DEFAULT '0' COMMENT '平',
  `lost` int DEFAULT '0' COMMENT '负',
  `points` int DEFAULT '0' COMMENT '积分',
  `goals_for` int DEFAULT '0' COMMENT '进球',
  `goals_against` int DEFAULT '0' COMMENT '失球',
  `goal_difference` int DEFAULT '0' COMMENT '净胜球',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `api_id` (`api_id`),
  KEY `idx_short_name` (`short_name`),
  KEY `idx_chinese_name` (`chinese_name`),
  KEY `idx_api_id` (`api_id`),
  KEY `idx_position` (`position`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='英超球队表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_sessions` (
  `token` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_accessed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`token`),
  KEY `idx_user_session_user_id` (`user_id`),
  KEY `idx_user_session_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
