# 后端部署文档

> 本文档记录所有为「微信云托管 CloudBase Run」容器化部署而做的代码改造，以及启动/部署方式。

---

## 一、本次为 CloudBase Run 直接修改的文件清单

### 1. 容器化部署基础设施

| 文件 | 改动说明 |
|---|---|
| `Dockerfile` | **新增** 多阶段构建：第一阶段用 `maven:3.9-eclipse-temurin-17` 编译打包，第二阶段用 `eclipse-temurin:17-jdk` 运行。启动命令带 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` 等 JVM 容器优化参数，让 Java 在云托管小规格实例里正确感知容器内存。 |
| `.dockerignore` | **新增** 排除 `.git/`、`target/`、`*.md`、`scripts/` 等非必要文件，减少镜像体积和构建时间。 |
| `.dockerignore` | 已存在（本次新增） |

### 2. 缓存层改造（移除 Redis 依赖）

| 文件 | 改动说明 |
|---|---|
| `pom.xml` | 删除 `spring-boot-starter-data-redis` 依赖；新增 `spring-boot-starter-cache` + `caffeine` 依赖。实现零 Redis 依赖部署，降低云托管成本。 |
| `src/main/java/.../config/RedisConfig.java` | **删除** 原 Redis 缓存配置。 |
| `src/main/java/.../config/CacheConfig.java` | **新增** Caffeine 本地缓存配置。定义了 30+ 个缓存名称及各自 TTL（资讯 5 分钟、赛程 1 分钟、积分榜 5 分钟、球队 1 小时、球员 12 小时等），完全覆盖原 Redis 缓存场景。 |
| `src/main/java/.../provider/FootballDataProvider.java` | 将注入的 `RedisTemplate` 替换为 `CacheManager`；内部 API 响应缓存逻辑不变，仍按不同接口分别缓存 60 秒 ~ 12 小时不等。 |

### 3. 应用配置适配

| 文件 | 改动说明 |
|---|---|
| `src/main/resources/application.yml` | `server.port` 从固定 `8080` 改为 `${PORT:8080}`，支持 CloudBase Run 通过环境变量动态注入端口。 |
| `src/main/resources/application.yml` | 删除全部 Redis 连接配置；将 `spring.cache.type` 从 `redis` 改为 `simple`（由 `CacheConfig` 接管）。 |
| `src/main/resources/application.yml` | 数据库连接池调优：`hikari.maximum-pool-size` 从 `20` 降到 `10`，`minimum-idle` 从 `5` 降到 `2`，降低小规格容器资源占用。 |
| `src/main/resources/application.yml` | 日志级别调为生产模式：`root: WARN`，`com.premierleague.server: INFO`，`org.springframework.web: INFO`；关闭 `hibernate.SQL` DEBUG，减少云托管日志费用。 |

### 4. 健康检查与存活探针

| 文件 | 改动说明 |
|---|---|
| `src/main/java/.../controller/HealthController.java` | **新增** 根路径 `/` 和 `/health` 接口。CloudBase Run 默认会访问根路径做服务存活探测，返回 `{"status":"UP"}` 确保服务被判定为健康。 |

### 5. 部署说明文档

| 文件 | 改动说明 |
|---|---|
| `CLOUD_RUN_DEPLOY.md` | **新增** 完整微信云托管部署指南，包含：MySQL 准备、GitHub 自动部署步骤、环境变量列表、实例配置建议、费用预估。 |
| `LICENSE` | 从 MIT 改为自定义非商业协议（与部署无关，但属于已 push 的改动）。 |
| `README.md` | 顶部新增醒目的许可证声明（与部署无关）。 |

---

## 二、未修改的业务代码

以下文件保持你本地原有的修改状态，**本次没有动过**：
- `DongqiudiProvider.java`
- `OfficialProvider.java`
- `RomanoProvider.java`
- `GuardianProvider.java` / `RedditProvider.java` / `SkyProvider.java`
- `ContentCleanService.java`
- `NewsFetchService.java`
- `HttpClientUtil.java`

---

## 三、本地启动方式

### 前提
- JDK 17+
- Maven 3.9+
- MySQL 8.0+（且已执行 `src/main/resources/db/schema.sql`）

### 启动命令
```bash
cd G:\Premier-League-miniapp-server
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-DDB_URL=jdbc:mysql://localhost:3306/premierleague?useSSL=false -DDB_USERNAME=root -DDB_PASSWORD=你的密码 -DFOOTBALL_DATA_TOKEN=你的Token"
```

或者先打包再运行：
```bash
mvn clean package -DskipTests
java -jar target/news-aggregator-0.0.1-SNAPSHOT.jar \
  --DB_URL=jdbc:mysql://localhost:3306/premierleague \
  --DB_USERNAME=root \
  --DB_PASSWORD=你的密码
```

访问验证：
- 服务根路径：`http://localhost:8080/`
- 业务 API 基路径：`http://localhost:8080/api/`
- 健康检查：`http://localhost:8080/health`

---

## 四、微信云托管 CloudBase Run 部署方式

### Step 1：准备 MySQL
1. 登录 [微信云托管控制台](https://cloud.weixin.qq.com/cloudrun)
2. 在云托管环境中一键购买 **TencentDB for MySQL**（最便宜的 1 核 1G 配置即可）
3. 用 MySQL 客户端连接，创建 `premierleague` 数据库并执行 `src/main/resources/db/schema.sql`

### Step 2：创建云托管服务
1. 控制台 → 新建服务 → 服务名：`premier-league-server`
2. 部署方式：**GitHub 仓库部署**
3. 选择仓库：`403938571Crist/Premier-League-miniapp-server`
4. 构建类型：**Dockerfile 构建**
5. 触发首次构建

### Step 3：配置环境变量
进入服务详情 → 版本配置 → 环境变量，添加：

```
DB_URL=jdbc:mysql://<TencentDB内网地址>:3306/premierleague?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=<你的数据库密码>
FOOTBALL_DATA_TOKEN=80e317eb06654d0e808e6b2021b19192
```

> 不需要配置任何 Redis 相关变量。

### Step 4：实例配置（关键）
因为服务内有 **Spring Scheduler 定时任务**，必须保证至少 1 个常驻实例：
- 最小实例数：**1**
- 最大实例数：**3**（根据流量调整）
- 容器规格：**0.25 核 0.5G 内存** 起步

### Step 5：发布并获取域名
部署成功后，云托管会分配 HTTPS 域名：
```
https://xxx.cloudrun.weixin.qq.com
```

把这个域名填到小程序前端的 `utils/env-config.js` 里即可。

---

## 五、费用预估（个人项目/初创）

| 资源 | 配置 | 预估月费 |
|---|---|---|
| 云托管 | 0.25核 0.5G，最小实例 1 | ¥ 20 ~ 50 |
| TencentDB MySQL | 1核 1G，50G 存储 | ¥ 30 ~ 60 |
| Redis | **已移除，0 费用** | ¥ 0 |
| 流量 | 初创低流量 | 基本在免费额度内 |
| **合计** | | **¥ 50 ~ 110 / 月** |

---

## 六、关键验证点

- [x] `mvn clean package -DskipTests` 编译通过
- [x] 零 Redis 依赖（pom.xml 中已删除）
- [x] 端口支持动态注入 `${PORT:8080}`
- [x] 根路径 `/` 和 `/health` 可访问
- [x] Dockerfile 多阶段构建可成功打出镜像
