# 英超资讯聚合服务 (Premier League News Aggregator)

Spring Boot 后端服务，为微信小程序提供英超资讯聚合 API。

> ⚠️ **许可证声明**
> 
> 本项目采用自定义协议授权。**禁止未经许可的商业使用**。商用请联系作者获取书面授权。
> 
> - 个人学习 / 教育 / 非商业用途：允许免费使用
> - 商业用途（包括但不限于商业产品、商业服务、销售、再许可）：**需书面授权**
> - 联系邮箱：403938571@qq.com
> - 完整协议内容请查看 [LICENSE](./LICENSE)

## 技术栈

- **Java 17** + **Spring Boot 3.3.5**
- **MySQL 8** - 数据持久化
- **Redis** - 缓存加速
- **JPA/Hibernate** - ORM
- **Spring Scheduler** - 定时任务

## 项目结构

```
server/
├── src/main/java/com/premierleague/server/
│   ├── controller/
│   │   ├── NewsController.java      # 资讯 API
│   │   ├── SocialController.java    # 社媒 API
│   │   └── admin/
│   │       └── AdminController.java # 管理后台
│   ├── service/
│   │   ├── NewsService.java         # 查询服务（带缓存）
│   │   ├── NewsFetchService.java    # 抓取服务
│   │   ├── ContentCleanService.java # 内容清洗
│   │   └── DuplicateCheckService.java # 去重检查
│   ├── scheduler/
│   │   └── NewsFetchScheduler.java  # 定时抓取任务
│   ├── provider/
│   │   ├── NewsProvider.java        # Provider 接口
│   │   ├── RomanoProvider.java      # 罗马诺 (Mock)
│   │   ├── XProvider.java           # X平台 (待配置)
│   │   ├── OfficialProvider.java    # 英超官方 (Mock)
│   │   ├── DongqiudiProvider.java   # 懂球帝 (Mock)
│   │   └── BilibiliProvider.java    # B站 (预留扩展)
│   ├── repository/
│   ├── entity/
│   └── config/
├── src/main/resources/
│   ├── application.yml
│   └── db/schema.sql
└── pom.xml
```

## 快速开始

### 1. 环境准备

需要安装:
- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 7.0+

**Windows 一键安装:**
```powershell
cd "G:\Premier League-app\server"
.\install-dependencies.ps1
```

### 2. 数据库配置

```bash
mysql -u root -p

CREATE DATABASE premierleague CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
source src/main/resources/db/schema.sql
```

### 3. 启动服务

```bash
mvn spring-boot:run
```

访问: http://localhost:8080

---

## 📡 前端 API

### 资讯列表
```http
GET /api/news?page=1&pageSize=10&sourceType=romano&tag=转会
```

### 资讯详情
```http
GET /api/news/{id}
```

### 转会快讯
```http
GET /api/news/transfers?source=romano&teamId=73
```

### 球员社媒
```http
GET /api/social/players?teamId=57&platform=X
```

---

## 🔧 管理后台 API

### 手动触发抓取
```bash
# 按频率触发
curl -X POST http://localhost:8080/api/admin/fetch/high

# 按源触发
curl -X POST http://localhost:8080/api/admin/fetch/source/romano
```

### 查看抓取日志
```bash
curl http://localhost:8080/api/admin/logs
```

### 查看统计
```bash
curl "http://localhost:8080/api/admin/stats?hours=24"
```

### 查看源状态
```bash
curl http://localhost:8080/api/admin/sources
```

### 健康检查
```bash
curl http://localhost:8080/api/admin/health
```

---

## ⚡ 抓取频率配置

```yaml
app:
  fetch:
    high-frequency:    # 每2分钟
      sources: [romano, x]
      status: 待配置 (需X API Token)
    medium-frequency:  # 每10分钟
      sources: [official, dongqiudi]
      status: Mock数据 (框架就绪)
    low-frequency:     # 每30分钟
      sources: []      # 预留扩展位
      # 预留: bilibili, douyin (待评估)
```

### 当前实现状态

| 源 | 状态 | 说明 |
|----|------|------|
| Romano | 🔴 待配置 | 需 X API Bearer Token |
| X (Twitter) | 🔴 待配置 | 需 X API Bearer Token |
| 懂球帝 | 🟡 Mock数据 | 框架已搭建，API待接入 |
| 英超官方 | 🟡 Mock数据 | 框架已搭建，API待接入 |
| Bilibili | 🔴 预留扩展 | 仅预留扩展位 |
| 抖音 | 🔴 预留扩展 | 仅预留扩展位 |

---

## 💾 缓存策略

| 接口 | 缓存时间 | Key示例 |
|------|---------|---------|
| /api/news | 5分钟 | `newsList::1::10::romano::转会` |
| /api/news/{id} | 30分钟 | `newsDetail::news-1001` |
| /api/news/transfers | 2分钟 | `transferNews::::73::` |
| /api/social/players | 6小时 | `socialPlayers::57:::X` |

---

## 🔄 数据流向

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  定时任务    │ → │   Provider   │ → │  内容清洗    │
│ (2/10/30min)│    │ (多源抓取)   │    │ (去重/标准化)│
└─────────────┘    └─────────────┘    └──────┬──────┘
                                              ↓
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   前端API    │ ← │  Redis缓存   │ ← │   MySQL     │
│             │    │             │    │  (主存储)    │
└─────────────┘    └─────────────┘    └─────────────┘
```

**重要**: 首页资讯流不走实时抓取，统一读数据库+缓存

---

## 🎯 核心字段

### News 表
```sql
source_published_at  # 源站发布时间（业务时间）
fingerprint          # MD5(title|sourceType|sourcePublishedAt)
fetched_at           # 抓取时间
content_updated_at   # 内容更新时间
fetch_batch_id       # 抓取批次
```

### FetchLog 表
```sql
batch_id             # yyyyMMddHHmmss_sourceType
new_count            # 新增条数
duplicate_count      # 去重条数
failed_count         # 失败条数
duration_ms          # 耗时
```

---

## 📝 添加新数据源

1. 实现 `NewsProvider` 接口:
```java
@Component
public class DongqiudiProvider implements NewsProvider {
    @Override
    public String getSourceType() { return "dongqiudi"; }
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        // 调用API或解析网页
    }
}
```

2. 在 `application.yml` 配置频率:
```yaml
app:
  fetch:
    medium-frequency:
      sources: [..., dongqiudi]
```

3. 自动参与定时调度

---

## 🔍 监控指标

通过 `/api/admin/stats` 可获取:
- 各源抓取成功率
- 去重率 (duplicate/request)
- 平均抓取耗时
- 新增数据趋势
- 失败任务列表

---

## 📄 相关文档

- `DATA_STRATEGY.md` - 数据策略详细设计
- `src/main/resources/db/schema.sql` - 数据库表结构
