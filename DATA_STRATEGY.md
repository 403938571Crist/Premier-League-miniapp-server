# 英超资讯聚合服务 - 数据策略设计

## 1. 核心设计原则

### 1.1 读写分离
- **写路径**：定时任务抓取 → 去重 → MySQL 落库
- **读路径**：API 查询 → Redis 缓存 → MySQL 数据库
- **绝不实时硬抓**：首页资讯流统一走服务端缓存结果

### 1.2 数据流图
```
┌─────────────────────────────────────────────────────────────┐
│                        定时抓取层                            │
├─────────────────────────────────────────────────────────────┤
│  高频源(2min)    中频源(10min)    低频源(30min)              │
│  ├─ Romano       ├─ 英超官方      ├─ [预留扩展]              │
│  └─ X            └─ 懂球帝        └─ [预留扩展]              │
└──────────────────────┬──────────────────────────────────────┘
                       │ 抓取
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                        处理层                                │
├─────────────────────────────────────────────────────────────┤
│  1. 生成指纹 MD5(title|sourceType|sourcePublishedAt)         │
│  2. 去重检查（fingerprint 唯一索引）                         │
│  3. 记录抓取日志（batch_id, new_count, duplicate_count）      │
└──────────────────────┬──────────────────────────────────────┘
                       │ 入库
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                        存储层                                │
├─────────────────────────────────────────────────────────────┤
│  MySQL (主存储)                                              │
│  ├── news 表：资讯内容 + 抓取元数据                           │
│  ├── fetch_log 表：抓取任务日志                              │
│  └── player_social 表：球员社媒                              │
│                                                               │
│  Redis (缓存)                                                │
│  ├── newsList::{page}:{size}:{source}:{tag}  → 5分钟         │
│  ├── newsDetail::{id}                         → 30分钟       │
│  ├── transferNews::{source}:{team}:{player}   → 2分钟        │
│  └── socialPlayers::{team}:{player}:{platform} → 6小时       │
└──────────────────────┬──────────────────────────────────────┘
                       │ 查询
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                        API 层                                │
├─────────────────────────────────────────────────────────────┤
│  GET /api/news              ← 缓存 5min                      │
│  GET /api/news/{id}         ← 缓存 30min                     │
│  GET /api/news/transfers    ← 缓存 2min                      │
│  GET /api/social/players    ← 缓存 6h                        │
└─────────────────────────────────────────────────────────────┘
```

> **当前状态**: 所有资讯源均使用 Mock 数据，架构和调度系统已就绪，真实API待接入。

---

## 2. 抓取频率分级

| 级别 | 频率 | 源 | 状态 | 说明 |
|------|------|-----|------|------|
| **高频** | 每2分钟 | Romano, X | 🔴 待配置 | 需X API Token |
| **中频** | 每10分钟 | 英超官方, 懂球帝 | 🟡 Mock数据 | 框架就绪 |
| **低频** | 每30分钟 | [预留扩展] | 🔴 预留位 | B站/抖音预留 |

### 预留扩展源
- **Bilibili** - 视频内容（预留扩展位，未实现）
- **抖音** - 短视频热点（预留扩展位，未实现）

---

## 3. 核心字段设计

### 3.1 News 表（抓取元数据）
```sql
-- 业务时间
source_published_at  DATETIME  -- 源站发布时间

-- 抓取元数据
fingerprint          VARCHAR(64) UNIQUE  -- 去重指纹
fetched_at           DATETIME            -- 首次抓取时间
content_updated_at   DATETIME            -- 内容更新时间
fetch_batch_id       VARCHAR(32)         -- 抓取批次号
fetch_status         VARCHAR(20)         -- pending/success/failed
fetch_error          VARCHAR(500)        -- 失败原因
```

### 3.2 指纹生成规则
```java
// MD5(title + "|" + sourceType + "|" + sourcePublishedAt)
String raw = "罗马诺：热刺接近签下新援中场|romano|2026-04-12T18:00:00";
String fingerprint = MD5(raw);
// → "a1b2c3d4e5f6..."
```

---

## 4. 抓取日志 (fetch_log)

记录每次抓取任务的完整信息：

| 字段 | 说明 |
|------|------|
| batch_id | 批次号：yyyyMMddHHmmss_sourceType |
| status | running / success / failed |
| duration_ms | 抓取耗时 |
| request_count | 请求条数 |
| new_count | 新增条数 |
| duplicate_count | 重复条数（去重） |
| failed_count | 失败条数 |

---

## 5. 缓存策略

### 5.1 缓存时间配置
```yaml
app:
  cache:
    newsList: 5min      # 首页资讯列表
    newsDetail: 30min   # 详情页
    transferNews: 2min  # 转会快讯（更新频繁）
    socialPlayers: 6h   # 球员社媒（变化慢）
```

### 5.2 缓存 Key 设计
```
newsList::1::10::romano::转会 → 首页第1页，来源romano，标签转会
newsDetail::news-1001        → 资讯详情
transferNews::::73::         → 球队73的转会快讯
socialPlayers::57:::X        → 阿森纳球员在X的社媒
```

---

## 6. 降级策略

当数据库无数据时，自动回退到 Mock 数据：

```java
// NewsService.java
public PageResult<NewsListItem> getNewsList(...) {
    Page<News> newsPage = newsRepository.findAll(...);
    
    if (newsPage.isEmpty()) {
        log.info("DB empty, fallback to mock data");
        return getMockNewsList(...);  // 兜底
    }
    
    return PageResult.of(...);
}
```

> **注意**: 当前所有源均返回 Mock 数据，真实数据抓取待实现。

---

## 7. 监控指标

可监控的指标：

| 指标 | 计算方式 |
|------|----------|
| 抓取成功率 | success_count / total_count |
| 去重率 | duplicate_count / request_count |
| 平均抓取耗时 | AVG(duration_ms) |
| 各源新增趋势 | new_count by source_type by hour |
| 缓存命中率 | Redis hits / (hits + misses) |

---

## 8. 扩展性设计

### 8.1 添加新数据源
1. 实现 `NewsProvider` 接口
2. 在 `FetchSourceProperties` 中配置频率
3. 自动参与定时调度

### 8.2 调整抓取频率
修改 `application.yml`：
```yaml
app:
  fetch:
    high-frequency:
      interval-minutes: 2  # 改为3分钟
```

### 8.3 预留扩展源接入
以下源已预留扩展位：
- **Bilibili** - 适合视频内容（需解决API认证）
- **抖音** - 适合短视频热点（需官方API权限）

---

## 9. 一致性保证

- **最终一致性**：抓取可能有延迟，但保证最终一致
- **幂等性**：指纹机制保证同一新闻不会重复入库
- **顺序性**：按 sourcePublishedAt 排序，不依赖抓取时间

---

## 10. 实现路线图

| 阶段 | 目标 | 状态 |
|------|------|------|
| **Phase 1** | 架构搭建、Mock数据、前端联调 | ✅ 已完成 |
| **Phase 2** | 接入懂球帝/英超官方 API | 🔴 待实现 |
| **Phase 3** | 接入 X API (Romano源) | 🔴 待实现 |
| **Phase 4** | 接入 B站/抖音（评估后决定） | 🔴 预留扩展 |
