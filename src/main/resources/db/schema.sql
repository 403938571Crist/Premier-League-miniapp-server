-- 创建数据库
CREATE DATABASE IF NOT EXISTS premierleague 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE premierleague;

-- ============================================
-- 球队表 (Teams)
-- ============================================
CREATE TABLE IF NOT EXISTS teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_id BIGINT UNIQUE COMMENT 'football-data.org API ID',
    name VARCHAR(100) NOT NULL COMMENT '球队全称',
    short_name VARCHAR(50) COMMENT '简称',
    chinese_name VARCHAR(50) COMMENT '中文名称',
    crest_url VARCHAR(500) COMMENT '队徽URL',
    venue VARCHAR(100) COMMENT '主场',
    founded INT COMMENT '成立年份',
    club_colors VARCHAR(50) COMMENT '俱乐部颜色',
    website VARCHAR(200) COMMENT '官方网站',
    
    -- 积分榜数据
    position INT COMMENT '排名',
    played_games INT DEFAULT 0 COMMENT '已赛场次',
    won INT DEFAULT 0 COMMENT '胜',
    draw INT DEFAULT 0 COMMENT '平',
    lost INT DEFAULT 0 COMMENT '负',
    points INT DEFAULT 0 COMMENT '积分',
    goals_for INT DEFAULT 0 COMMENT '进球',
    goals_against INT DEFAULT 0 COMMENT '失球',
    goal_difference INT DEFAULT 0 COMMENT '净胜球',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_short_name (short_name),
    INDEX idx_chinese_name (chinese_name),
    INDEX idx_api_id (api_id),
    INDEX idx_position (position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='英超球队表';

-- ============================================
-- 比赛表 (Matches)
-- ============================================
CREATE TABLE IF NOT EXISTS matches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_id BIGINT UNIQUE COMMENT 'football-data.org API ID',
    season VARCHAR(20) COMMENT '赛季',
    matchday INT COMMENT '轮次',
    match_date DATETIME COMMENT '比赛时间',
    status VARCHAR(20) COMMENT '状态: SCHEDULED/LIVE/IN_PLAY/FINISHED',
    
    -- 主队
    home_team_id BIGINT COMMENT '主队ID',
    home_team_name VARCHAR(100) COMMENT '主队名',
    home_team_chinese_name VARCHAR(50) COMMENT '主队中文名',
    home_team_crest VARCHAR(500) COMMENT '主队队徽',
    
    -- 客队
    away_team_id BIGINT COMMENT '客队ID',
    away_team_name VARCHAR(100) COMMENT '客队名',
    away_team_chinese_name VARCHAR(50) COMMENT '客队中文名',
    away_team_crest VARCHAR(500) COMMENT '客队队徽',
    
    -- 比分
    home_score INT COMMENT '主队比分',
    away_score INT COMMENT '客队比分',
    home_half_score INT COMMENT '主队半场比分',
    away_half_score INT COMMENT '客队半场比分',
    duration INT COMMENT '比赛时长(分钟)',
    stage VARCHAR(20) COMMENT '阶段: REGULAR_TIME/EXTRA_TIME/PENALTIES',
    
    venue VARCHAR(100) COMMENT '比赛场地',
    referee VARCHAR(100) COMMENT '裁判',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_match_date (match_date),
    INDEX idx_status (status),
    INDEX idx_matchday (matchday),
    INDEX idx_home_team (home_team_id),
    INDEX idx_away_team (away_team_id),
    INDEX idx_api_id (api_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='英超赛程/比赛表';

-- ============================================
-- 球员表 (Players)
-- ============================================
CREATE TABLE IF NOT EXISTS players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_id BIGINT UNIQUE COMMENT 'football-data.org API ID',
    team_id BIGINT COMMENT '所属球队ID',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    chinese_name VARCHAR(50) COMMENT '中文名',
    photo_url VARCHAR(500) COMMENT '头像URL',
    shirt_number VARCHAR(10) COMMENT '球衣号码',
    position VARCHAR(20) COMMENT '位置: Goalkeeper/Defender/Midfielder/Attacker',
    chinese_position VARCHAR(20) COMMENT '中文位置',
    nationality VARCHAR(50) COMMENT '国籍',
    date_of_birth DATE COMMENT '出生日期',
    height INT COMMENT '身高(cm)',
    weight INT COMMENT '体重(kg)',
    foot VARCHAR(10) COMMENT '惯用脚: Left/Right',
    contract_until DATE COMMENT '合同到期日',
    market_value BIGINT COMMENT '市场价值(欧元)',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_team_id (team_id),
    INDEX idx_position (position),
    INDEX idx_api_id (api_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='球员表';

-- ============================================
-- 资讯表
-- ============================================
CREATE TABLE IF NOT EXISTS news (
    id VARCHAR(64) PRIMARY KEY COMMENT '使用指纹作为ID',
    title VARCHAR(500) NOT NULL COMMENT '标题',
    summary VARCHAR(2000) NOT NULL COMMENT '摘要',
    source VARCHAR(100) NOT NULL COMMENT '来源名称',
    source_type VARCHAR(32) NOT NULL COMMENT '来源类型',
    media_type VARCHAR(32) NOT NULL COMMENT '媒体类型',
    source_published_at DATETIME NOT NULL COMMENT '源站发布时间',
    author VARCHAR(100) COMMENT '作者',
    cover_image VARCHAR(500) COMMENT '封面图URL',
    tags VARCHAR(500) COMMENT '标签',
    related_team_ids VARCHAR(500) COMMENT '关联球队ID',
    related_player_ids VARCHAR(500) COMMENT '关联球员ID',
    hot_score INT DEFAULT 0 COMMENT '热度值',
    url VARCHAR(500) COMMENT '原文链接',
    source_note VARCHAR(200) COMMENT '来源说明',
    content TEXT COMMENT '正文内容',
    fingerprint VARCHAR(64) UNIQUE COMMENT '去重指纹',
    fetched_at DATETIME COMMENT '抓取时间',
    content_updated_at DATETIME COMMENT '内容更新时间',
    fetch_batch_id VARCHAR(32) COMMENT '抓取批次号',
    fetch_status VARCHAR(20) DEFAULT 'success' COMMENT '抓取状态',
    fetch_error VARCHAR(500) COMMENT '抓取失败原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_source_type (source_type),
    INDEX idx_media_type (media_type),
    INDEX idx_source_published_at (source_published_at),
    INDEX idx_hot_score (hot_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='资讯表';

-- ============================================
-- 同步日志表 (Sync Logs)
-- ============================================
CREATE TABLE IF NOT EXISTS sync_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type VARCHAR(30) NOT NULL COMMENT '同步类型: STANDINGS/MATCHES/TEAMS/SQUAD/PLAYERS',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    status VARCHAR(20) NOT NULL COMMENT '状态: SUCCESS/FAILED/PARTIAL',
    items_count INT COMMENT '同步数据量',
    success_count INT COMMENT '成功数量',
    fail_count INT COMMENT '失败数量',
    source VARCHAR(50) COMMENT '数据源',
    duration_ms BIGINT COMMENT '耗时（毫秒）',
    error_message VARCHAR(2000) COMMENT '错误信息',
    detail_log VARCHAR(4000) COMMENT '详细日志',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_sync_type (sync_type),
    INDEX idx_sync_time (sync_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='数据同步日志表';

-- ============================================
-- 插入示例数据 - 球队 (积分榜数据)
-- ============================================
INSERT INTO teams (api_id, name, short_name, chinese_name, crest_url, venue, founded, position, played_games, won, draw, lost, points, goals_for, goals_against, goal_difference) VALUES
(57, 'Arsenal FC', 'Arsenal', '阿森纳', 'https://crests.football-data.org/57.png', 'Emirates Stadium', 1886, 1, 32, 22, 4, 6, 70, 75, 37, 38),
(65, 'Manchester City FC', 'Man City', '曼城', 'https://crests.football-data.org/65.png', 'Etihad Stadium', 1880, 2, 30, 19, 4, 7, 61, 62, 30, 32),
(66, 'Manchester United FC', 'Man United', '曼联', 'https://crests.football-data.org/66.png', 'Old Trafford', 1878, 3, 31, 16, 7, 8, 55, 48, 35, 13),
(58, 'Aston Villa FC', 'Aston Villa', '维拉', 'https://crests.football-data.org/58.png', 'Villa Park', 1874, 4, 32, 16, 7, 9, 55, 52, 47, 5),
(64, 'Liverpool FC', 'Liverpool', '利物浦', 'https://crests.football-data.org/64.png', 'Anfield', 1892, 5, 32, 15, 7, 10, 52, 59, 49, 10),
(61, 'Chelsea FC', 'Chelsea', '切尔西', 'https://crests.football-data.org/61.png', 'Stamford Bridge', 1905, 6, 31, 14, 6, 11, 48, 49, 34, 15),
(402, 'Brentford FC', 'Brentford', '布伦特福德', 'https://crests.football-data.org/402.png', 'Gtech Community Stadium', 1889, 7, 32, 13, 8, 11, 47, 49, 45, 4),
(62, 'Everton FC', 'Everton', '埃弗顿', 'https://crests.football-data.org/62.png', 'Goodison Park', 1878, 8, 32, 12, 11, 9, 47, 38, 36, 2),
(397, 'Brighton & Hove Albion FC', 'Brighton', '布莱顿', 'https://crests.football-data.org/397.png', 'Amex Stadium', 1901, 9, 32, 12, 10, 10, 46, 52, 46, 6),
(1044, 'AFC Bournemouth', 'Bournemouth', '伯恩茅斯', 'https://crests.football-data.org/1044.png', 'Vitality Stadium', 1899, 10, 32, 12, 9, 11, 45, 48, 49, -1);

-- ============================================
-- 插入示例数据 - 比赛 (第32轮)
-- ============================================
INSERT INTO matches (api_id, season, matchday, match_date, status, home_team_id, home_team_name, home_team_chinese_name, home_team_crest, away_team_id, away_team_name, away_team_chinese_name, away_team_crest, home_score, away_score, venue, referee) VALUES
(1, '2025-2026', 32, '2026-04-12 21:00:00', 'LIVE', 1068, 'Sunderland AFC', '桑德兰', 'https://crests.football-data.org/1068.png', 73, 'Tottenham Hotspur FC', '热刺', 'https://crests.football-data.org/73.png', 0, 0, 'Stadium of Light', 'Anthony Taylor'),
(2, '2025-2026', 32, '2026-04-12 21:00:00', 'LIVE', 354, 'Crystal Palace FC', '水晶宫', 'https://crests.football-data.org/354.png', 67, 'Newcastle United FC', '纽卡斯尔', 'https://crests.football-data.org/67.png', 0, 0, 'Selhurst Park', 'Michael Oliver'),
(3, '2025-2026', 32, '2026-04-12 21:00:00', 'LIVE', 351, 'Nottingham Forest FC', '森林', 'https://crests.football-data.org/351.png', 58, 'Aston Villa FC', '维拉', 'https://crests.football-data.org/58.png', 0, 0, 'City Ground', 'Simon Hooper'),
(4, '2025-2026', 32, '2026-04-13 00:30:00', 'SCHEDULED', 57, 'Arsenal FC', '阿森纳', 'https://crests.football-data.org/57.png', 61, 'Chelsea FC', '切尔西', 'https://crests.football-data.org/61.png', NULL, NULL, 'Emirates Stadium', 'Paul Tierney'),
(5, '2025-2026', 32, '2026-04-13 21:00:00', 'SCHEDULED', 65, 'Manchester City FC', '曼城', 'https://crests.football-data.org/65.png', 64, 'Liverpool FC', '利物浦', 'https://crests.football-data.org/64.png', NULL, NULL, 'Etihad Stadium', 'Chris Kavanagh');

-- ============================================
-- 插入示例数据 - 球员 (曼联阵容)
-- ============================================
INSERT INTO players (api_id, team_id, name, chinese_name, photo_url, shirt_number, position, chinese_position, nationality, date_of_birth, height, weight, foot) VALUES
-- 门将
(1, 66, 'Tom Heaton', NULL, NULL, '22', 'Goalkeeper', '门将', 'England', '1986-04-15', 188, 85, 'Right'),
(2, 66, 'Altay Bayındır', '巴因迪尔', NULL, '1', 'Goalkeeper', '门将', 'Turkey', '1998-04-14', 198, 89, 'Right'),
(3, 66, 'Senne Lammens', NULL, NULL, '50', 'Goalkeeper', '门将', 'Belgium', '2002-06-07', 185, 78, 'Right'),

-- 后卫
(4, 66, 'Tyler Fredricson', NULL, NULL, '43', 'Defender', '后卫', 'England', '2003-02-23', 186, 75, 'Right'),
(5, 66, 'Diego León', NULL, NULL, '26', 'Defender', '后卫', 'Paraguay', '2006-01-03', 178, 70, 'Left'),
(6, 66, 'Godwill Kukonki', NULL, NULL, '51', 'Defender', '后卫', 'England', '2006-08-08', 182, 72, 'Right'),
(7, 66, 'Lisandro Martínez', '利桑德罗', NULL, '6', 'Defender', '后卫', 'Argentina', '1998-01-18', 175, 77, 'Left'),
(8, 66, 'Harry Maguire', '马奎尔', NULL, '5', 'Defender', '后卫', 'England', '1993-03-05', 194, 96, 'Right'),

-- 中场
(9, 66, 'Bruno Fernandes', 'B费', NULL, '8', 'Midfielder', '中场', 'Portugal', '1994-09-08', 179, 69, 'Right'),
(10, 66, 'Casemiro', '卡塞米罗', NULL, '18', 'Midfielder', '中场', 'Brazil', '1992-02-23', 185, 84, 'Right'),
(11, 66, 'Kobbie Mainoo', '梅努', NULL, '37', 'Midfielder', '中场', 'England', '2005-04-19', 175, 68, 'Right'),
(12, 66, 'Mason Mount', '芒特', NULL, '7', 'Midfielder', '中场', 'England', '1999-01-10', 181, 74, 'Right'),

-- 前锋
(13, 66, 'Marcus Rashford', '拉什福德', NULL, '10', 'Attacker', '前锋', 'England', '1997-10-31', 185, 70, 'Right'),
(14, 66, 'Rasmus Højlund', '霍伊伦', NULL, '9', 'Attacker', '前锋', 'Denmark', '2003-02-04', 191, 79, 'Left'),
(15, 66, 'Alejandro Garnacho', '加纳乔', NULL, '17', 'Attacker', '前锋', 'Argentina', '2004-07-01', 180, 71, 'Right');

-- ============================================
-- App users / sessions / follows
-- ============================================
CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(100) NULL,
    avatar_url VARCHAR(500) NULL,
    guest TINYINT(1) NOT NULL DEFAULT 1,
    external_open_id VARCHAR(100) NULL,
    auth_provider VARCHAR(32) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_user_external_open_id (external_open_id),
    INDEX idx_app_user_display_name (display_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_sessions (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(64) NULL,
    expires_at DATETIME NOT NULL,
    last_accessed_at DATETIME NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_session_user_id (user_id),
    INDEX idx_user_session_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS followed_teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_followed_team_user_team (user_id, team_id),
    INDEX idx_followed_team_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
