# 英超小程序后端数据抓取实现说明

这份文档是给后端开发直接落地用的。

目标很明确：

1. 赛程、积分榜、球队详情、球员详情全部由服务端统一提供接口
2. 前端不直连第三方数据源
3. 不使用 mock 数据
4. 对结构化数据优先使用正式 API，不要先去爬网页

---

## 1. 先定原则

### 1.1 结构化数据不要优先爬网页

这类数据：

- 积分榜
- 赛程
- 比赛详情
- 球队详情
- 阵容
- 球员基础资料

都属于结构化数据。

对于结构化数据，优先级必须是：

1. 正式 API
2. 稳定 RSS / feed
3. 最后才是 HTML 抓取

原因：

- API 结构稳定，字段清晰
- 不容易因为网页改版直接失效
- 更适合做缓存、落库、增量更新
- 更适合 Spring Boot 服务端标准化输出

所以这部分不要让 Kimi 去写 Jsoup 到处抓官网页面。

---

## 2. 推荐主数据源

### 2.1 主源：football-data.org

对于英超小程序当前 MVP，结构化主源直接统一用 `football-data.org v4`。

它适合覆盖：

- 英超积分榜
- 英超赛程
- 比赛详情
- 球队基础资料
- 球队阵容
- 球队赛程
- 球员基础资料

官方文档：

- https://www.football-data.org/documentation/quickstart
- https://docs.football-data.org/general/v4/index.html
- https://docs.football-data.org/general/v4/competition.html
- https://docs.football-data.org/general/v4/team.html
- https://docs.football-data.org/general/v4/person.html
- https://docs.football-data.org/general/v4/match.html
- https://docs.football-data.org/general/v4/policies.html

关键约束：

- 免费版限流：`10 requests / minute`
- 请求头使用：`X-Auth-Token`
- 英超 competition code：`PL`

结论：

- 赛程、积分榜、球队、球员先不要自己“爬”
- 先把 `football-data.org` 当主源接好

---

## 3. 小程序页面与上游接口映射

### 3.1 首页焦点比赛 / 今日比赛

上游接口建议：

```http
GET /v4/competitions/PL/matches?dateFrom=2026-04-12&dateTo=2026-04-13
```

用途：

- 首页焦点比赛
- 今日比赛列表
- 比赛状态更新

服务端对外接口建议：

```http
GET /api/matches/today
GET /api/matches/highlights
```

---

### 3.2 赛程页

上游接口建议：

```http
GET /v4/competitions/PL/matches?dateFrom=2026-04-12&dateTo=2026-04-13
GET /v4/competitions/PL/matches?matchday=32
GET /v4/matches/{id}
```

覆盖能力：

- 按日期查赛程
- 按轮次查赛程
- 比赛详情

服务端对外接口建议：

```http
GET /api/matches?date=2026-04-12
GET /api/matches?matchday=32
GET /api/matches/{id}
```

---

### 3.3 积分榜页

上游接口建议：

```http
GET /v4/competitions/PL/standings
```

这个接口本身就会返回：

- `TOTAL`
- `HOME`
- `AWAY`

所以前端需要的：

- 总榜
- 主场
- 客场

服务端不需要拆成三个外部请求，一次拉取后自己按 `type` 分流即可。

服务端对外接口建议：

```http
GET /api/teams/standings
GET /api/teams/standings?type=TOTAL
GET /api/teams/standings?type=HOME
GET /api/teams/standings?type=AWAY
```

---

### 3.4 球队详情页

上游接口建议：

```http
GET /v4/teams/{id}
GET /v4/teams/{id}/matches?dateFrom=2026-04-01&dateTo=2026-05-01
```

`/v4/teams/{id}` 一般能覆盖：

- 队名
- 缩写
- 队徽
- 主场
- 成立时间
- 网址
- 主教练
- 阵容

服务端对外接口建议：

```http
GET /api/teams/{id}
GET /api/teams/{id}/matches
GET /api/teams/{id}/squad
```

说明：

- 如果前端的“概览 / 阵容 / 赛程”都在球队详情页里，服务端最好还是拆接口，不要把所有东西塞一个超大响应。

---

### 3.5 球员详情页

上游接口建议：

```http
GET /v4/persons/{id}
GET /v4/persons/{id}/matches?limit=10
```

可覆盖：

- 姓名
- 生日
- 国籍
- 位置
- 号码
- 当前球队
- 最近比赛

服务端对外接口建议：

```http
GET /api/players/{id}
GET /api/players/{id}/matches
```

注意：

- `football-data.org` 更偏基础资料，不一定给出你想要的全部赛季统计
- 如果后续要更深的球员统计，再考虑第二数据源

---

## 4. 不同数据该怎么抓

### 4.1 实时性高的数据

包括：

- 今日赛程
- 进行中的比赛
- 焦点比赛

策略：

- 从上游 API 抓
- 先缓存到 Redis
- 需要时再异步落 MySQL

推荐刷新频率：

- 比赛进行中：`1 分钟`
- 比赛日前后：`2-5 分钟`
- 普通时段：`10 分钟`

---

### 4.2 相对稳定的数据

包括：

- 积分榜
- 球队基础资料
- 球队阵容
- 球员基础资料

策略：

- 定时任务抓
- MySQL 持久化
- Redis 缓存热点数据

推荐刷新频率：

- 积分榜：`5-10 分钟`
- 球队资料：`12 小时`
- 球队阵容：`6-12 小时`
- 球员基础资料：`12-24 小时`

---

## 5. 缓存建议

### 5.1 Redis 负责热缓存

建议缓存：

- `matches:today:{date}`
- `matches:byDate:{date}`
- `matches:byMatchday:{matchday}`
- `match:detail:{matchId}`
- `standings:PL`
- `standings:PL:TOTAL`
- `standings:PL:HOME`
- `standings:PL:AWAY`
- `team:detail:{teamId}`
- `team:squad:{teamId}`
- `team:matches:{teamId}:{dateFrom}:{dateTo}`
- `player:detail:{playerId}`

建议 TTL：

- 今日赛程：`60s`
- 比赛详情：`60-120s`
- 积分榜：`300s`
- 球队资料：`6h`
- 阵容：`6h`
- 球员资料：`12h`

---

### 5.2 MySQL 负责落库

建议落库的表至少有：

- `matches`
- `match_scores`
- `standings_snapshots`
- `teams`
- `players`
- `team_squad_rel`
- `sync_logs`

说明：

- 如果第一阶段来不及全落库，可以先落 `teams / players / standings_snapshots`
- 赛程可以先走 Redis + 定时同步

---

## 6. 服务端实现方式

### 6.1 不要把 Controller 直接写成代理转发

错误做法：

- 前端请求 `/api/matches`
- 后端 controller 里直接去请求第三方
- 原样返回第三方字段

正确做法：

1. `Provider` 层负责调用第三方
2. `Service` 层负责字段标准化、缓存、容错
3. `Controller` 层只返回你们自己的 DTO

建议分层：

```text
controller
service
provider
repository
entity
dto
config
```

---

### 6.2 Provider 设计建议

建议专门做一个：

```java
FootballDataProvider
```

只负责这几类方法：

```java
List<MatchDto> fetchMatchesByDate(LocalDate date)
List<MatchDto> fetchMatchesByMatchday(Integer matchday)
MatchDetailDto fetchMatchDetail(Long matchId)
StandingsDto fetchStandings()
TeamDto fetchTeam(Long teamId)
List<PlayerDto> fetchTeamSquad(Long teamId)
List<MatchDto> fetchTeamMatches(Long teamId, LocalDate from, LocalDate to)
PlayerDto fetchPlayer(Long playerId)
List<MatchDto> fetchPlayerMatches(Long playerId, int limit)
```

不要把所有逻辑塞进一个巨大的 provider 方法。

---

### 6.3 状态映射要统一

第三方状态不要直接透给前端。

至少做一层统一状态映射：

| 上游状态 | 前端状态 |
|---|---|
| `SCHEDULED` | `NOT_STARTED` |
| `TIMED` | `NOT_STARTED` |
| `IN_PLAY` | `LIVE` |
| `PAUSED` | `LIVE` |
| `FINISHED` | `FINISHED` |
| `POSTPONED` | `POSTPONED` |
| `CANCELLED` | `CANCELLED` |

前端只认自己的一套状态。

---

## 7. 限流处理

`football-data.org` 免费版 `10 req/min`，所以不能瞎刷。

必须做这些事：

1. 同一个日期的赛程请求先走 Redis
2. 同一个球队详情先走 Redis
3. 积分榜统一由定时任务刷新，不要每次前端访问都去打上游
4. 比赛详情允许短 TTL 热缓存
5. 加抓取日志，记录每次上游请求次数

推荐做一个简单的上游请求门禁：

- 每个 provider 请求前记录时间
- 超过速率时直接读缓存或返回最近一次同步结果

---

## 8. Kimi 应该怎么实现

### 第一阶段先完成

1. `FootballDataProvider`
2. `MatchService`
3. `StandingsService`
4. `TeamService`
5. `PlayerService`
6. Redis 缓存接入
7. 基础同步日志

### 第一阶段接口

```http
GET /api/matches?date=2026-04-12
GET /api/matches?matchday=32
GET /api/matches/{id}

GET /api/teams/standings
GET /api/teams/standings?type=TOTAL
GET /api/teams/standings?type=HOME
GET /api/teams/standings?type=AWAY

GET /api/teams/{id}
GET /api/teams/{id}/squad
GET /api/teams/{id}/matches

GET /api/players/{id}
GET /api/players/{id}/matches
```

### 第二阶段再做

- 比赛交锋记录
- 球员深度赛季统计
- 更多媒体源
- 更复杂的数据落库与快照

---

## 9. 不要做的事

### 9.1 不要先写 mock

你现在的目标是接真实数据，所以：

- 不要再造一套 mock fixtures
- 不要再造 mock standings
- 不要再造 mock teams
- 不要再造 mock players

没有数据时就返回：

- 空列表
- 空状态
- 或最近一次同步结果

但不要伪造业务数据。

---

### 9.2 不要先写一堆网页爬虫

对赛程、积分榜、球队资料这些结构化数据，不要一开始就：

- Jsoup 抓官网 HTML
- Selenium 跑浏览器
- 正则抠网页字段

这会让系统非常脆。

---

## 10. 给 Kimi 的一句话要求

可以直接发给 Kimi：

```text
请先按 MATCH_TEAM_DATA_FETCH_GUIDE.md 实现后端结构化数据接入。

要求：
1. 赛程、积分榜、球队详情、阵容、球员详情统一走 football-data.org v4
2. 不要 mock 数据
3. 不要先做网页爬虫
4. 服务端统一做字段映射、状态映射、缓存、限流保护
5. 前端只对接我们自己的 /api 接口
6. 第一阶段先完成 matches / standings / teams / players 相关接口
```

---

## 11. 参考资料

- football-data quickstart: https://www.football-data.org/documentation/quickstart
- football-data overview: https://docs.football-data.org/general/v4/index.html
- competition + standings: https://docs.football-data.org/general/v4/competition.html
- team resource: https://docs.football-data.org/general/v4/team.html
- person resource: https://docs.football-data.org/general/v4/person.html
- match resource: https://docs.football-data.org/general/v4/match.html
- API policies / rate limit: https://docs.football-data.org/general/v4/policies.html
