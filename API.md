# 英超小程序后端 API 文档

## 基础信息

- **Base URL**: `http://localhost:8080/api`
- **统一返回格式**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

---

## 一、资讯 API

### 1. 获取资讯列表
```http
GET /api/news?page=1&pageSize=10&sourceType=romano&tag=转会
```

**参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页条数，默认10 |
| sourceType | string | 否 | 来源筛选 |
| tag | string | 否 | 标签筛选 |
| keyword | string | 否 | 关键词搜索 |

### 2. 获取资讯详情
```http
GET /api/news/{id}
```

### 3. 获取转会快讯
```http
GET /api/news/transfers?source=romano&teamId=73
```

### 4. 获取球员社媒
```http
GET /api/social/players?teamId=57&platform=X
```

---

## 二、积分榜/球队 API

### 1. 获取积分榜
```http
GET /api/teams/standings
```

**返回示例**:
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "name": "Arsenal FC",
      "chineseName": "阿森纳",
      "crestUrl": "https://crests.football-data.org/57.png",
      "position": 1,
      "playedGames": 32,
      "won": 22,
      "draw": 4,
      "lost": 6,
      "points": 70,
      "goalsFor": 75,
      "goalsAgainst": 37,
      "goalDifference": 38
    }
  ]
}
```

### 2. 获取所有球队
```http
GET /api/teams
```

### 3. 获取球队详情
```http
GET /api/teams/{id}
```

### 4. 获取球队阵容
```http
GET /api/teams/{id}/squad
```

**返回示例**:
```json
{
  "code": 0,
  "data": {
    "goalkeepers": [...],
    "defenders": [...],
    "midfielders": [...],
    "attackers": [...],
    "totalCount": 15
  }
}
```

### 5. 获取球队赛程
```http
GET /api/teams/{id}/matches
```

### 6. 获取球队统计
```http
GET /api/teams/{id}/stats
```

---

## 三、赛程 API

### 1. 获取今日比赛
```http
GET /api/matches/today
```

**返回示例**:
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "matchday": 32,
      "matchDate": "2026-04-12T21:00:00",
      "status": "LIVE",
      "homeTeamName": "Sunderland AFC",
      "homeTeamChineseName": "桑德兰",
      "homeTeamCrest": "https://crests.football-data.org/1068.png",
      "homeScore": 0,
      "awayTeamName": "Tottenham Hotspur FC",
      "awayTeamChineseName": "热刺",
      "awayTeamCrest": "https://crests.football-data.org/73.png",
      "awayScore": 0
    }
  ]
}
```

### 2. 获取进行中的比赛
```http
GET /api/matches/live
```

### 3. 获取某轮比赛
```http
GET /api/matches?matchday=32
```

### 4. 获取当前轮次
```http
GET /api/matches/current
```

### 5. 获取比赛详情
```http
GET /api/matches/{id}
```

### 6. 获取两队交锋记录
```http
GET /api/matches/headtohead?team1=57&team2=61
```

### 7. 获取日期范围比赛
```http
GET /api/matches/range?start=2026-04-12&end=2026-04-19
```

---

## 四、管理 API

### 1. 手动触发抓取
```http
POST /api/admin/fetch/high      # 高频源
POST /api/admin/fetch/medium    # 中频源
POST /api/admin/fetch/low       # 低频源
POST /api/admin/fetch/source/dongqiudi  # 指定源
```

### 2. 查看抓取日志
```http
GET /api/admin/logs
```

### 3. 查看抓取统计
```http
GET /api/admin/stats?hours=24
```

### 4. 查看源状态
```http
GET /api/admin/sources
```

### 5. 健康检查
```http
GET /api/admin/health
```

---

## 缓存策略

| 接口 | 缓存时间 | 说明 |
|------|----------|------|
| 资讯列表 | 5分钟 | 首页数据 |
| 资讯详情 | 30分钟 | 详情页 |
| 转会快讯 | 2分钟 | 实时性高 |
| **积分榜** | **5分钟** | 定期更新 |
| **球队列表** | **1小时** | 相对稳定 |
| **球队阵容** | **1小时** | 相对稳定 |
| **今日比赛** | **1分钟** | 接近实时 |
| **进行中比赛** | **30秒** | **实时比分** |
| **某轮比赛** | **5分钟** | 赛程数据 |

---

## 数据来源

### 当前状态说明

**当前所有资讯源均使用 Mock 数据**，真实抓取待后续实现：

| 源 | 当前状态 | 说明 |
|----|---------|------|
| **懂球帝** | 🟡 Mock 数据 | 框架已搭建，真实API待接入 |
| **Bilibili** | 🟡 Mock 数据 | 框架已搭建，真实API待接入 |
| **英超官方** | 🟡 Mock 数据 | 框架已搭建，真实API待接入 |
| **Fabrizio Romano** | 🔴 待配置 | 需 X API Bearer Token |
| **X (Twitter)** | 🔴 待配置 | 需 X API Bearer Token |
| **抖音** | 🔴 预留扩展 | 尚未实现 |

### 静态数据
- **球队/积分榜** - SQL初始化数据
- **赛程** - SQL初始化数据
- **球员** - SQL初始化数据

### 预留扩展源
以下源已预留扩展位，可后续接入：
- **Bilibili** - 视频内容（预留）
- **抖音** - 短视频热点（预留）
