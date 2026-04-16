# Premier League Server - 部署文档

> 本文档面向微信云托管 CloudBase Run 容器化部署，包含代码改造说明、环境变量清单、本地验证、生产部署全流程。

---

## 一、为 CloudBase Run 做的代码改造

### 1. 容器化部署文件

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 多阶段构建：Maven 编译 → JRE 运行。镜像内已默认启用 `prod` profile，并带 JVM 容器优化参数。 |
| `.dockerignore` | 排除 `.git/`、`target/`、`.env*`、IDE 配置等非生产文件，减小镜像体积。 |
| `docker-compose.yml` | 本地 Docker 验证用，可一键 `docker-compose up --build`。 |

### 2. 移除 Redis，改 Caffeine 本地缓存

| 文件 | 改动 |
|---|---|
| `pom.xml` | 删除 `spring-boot-starter-data-redis`，新增 `spring-boot-starter-cache` + `caffeine` |
| `CacheConfig.java` | 新增 Caffeine 配置，覆盖 30+ 缓存名称及 TTL |
| `RedisConfig.java` | **已删除** |
| `FootballDataProvider.java` | `RedisTemplate` 替换为 `CacheManager`，API 缓存行为不变 |

### 3. 端口与网络适配

| 文件 | 改动 |
|---|---|
| `application.yml` | `server.port` 改为 `${PORT:8080}`，支持云托管动态注入；新增 `server.address: 0.0.0.0`，确保容器内可访问 |
| `HealthController.java` | 新增 `/` 和 `/health` 接口，用于云托管存活探针 |

### 4. 生产配置增强

| 文件 | 改动 |
|---|---|
| `application-prod.yml` | 新增生产 profile：JPA `ddl-auto: validate`、精简日志、缓存确认 |
| `Dockerfile` | 默认注入 `SPRING_PROFILES_ACTIVE=prod` |
| `application.yml` | 连接池调小（max 10 / min 2）、Tomcat 线程限制（max 50）、优雅停机、Actuator 探针 |

### 5. 安全清理

- `application.yml` 中 **删除** 了硬编码的 football-data API key 默认值，强制生产环境通过环境变量注入。

---

## 二、环境变量清单

### 必填（无默认值，必须配置）

| 变量名 | 示例值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://10.x.x.x:3306/premierleague?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` | MySQL 连接地址 |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `你的密码` | 数据库密码 |

### 强烈建议配置（功能降级风险）

| 变量名 | 示例值 | 说明 |
|---|---|---|
| `FOOTBALL_DATA_TOKEN` | `你的_private_key` | football-data.org API Token。申请地址：https://www.football-data.org/ |

### 可选配置（有默认值，按需覆盖）

| 变量名 | 默认值 | 说明 |
|---|---|---|
| `PORT` | `8080` | 服务端口。CloudBase Run 会自动注入，本地开发可忽略 |
| `X_BEARER_TOKEN` | 空字符串 | X (Twitter) API Token。未配置时 RomanoProvider fallback 到公开 RSS 源 |
| `JAVA_OPTS` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Shanghai` | JVM 参数。可在 CloudBase Run 环境变量中覆盖 |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring Profile。Dockerfile 已默认注入，通常无需修改 |

> 所有环境变量映射模板可参考 `.env.example`。

---

## 三、本地启动方式

### 方式 A：直接 Maven 启动（推荐开发调试）

```bash
cd G:\Premier-League-miniapp-server

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="
    -DDB_URL=jdbc:mysql://localhost:3306/premierleague?useSSL=false
    -DDB_USERNAME=root
    -DDB_PASSWORD=你的密码
    -DFOOTBALL_DATA_TOKEN=你的Token
  "
```

访问验证：
- `http://localhost:8080/`
- `http://localhost:8080/health`
- `http://localhost:8080/api/teams/standings`

### 方式 B：先打包再运行

```bash
cd G:\Premier-League-miniapp-server
mvn clean package -DskipTests

java -jar target/news-aggregator-0.0.1-SNAPSHOT.jar \
  --DB_URL=jdbc:mysql://localhost:3306/premierleague \
  --DB_USERNAME=root \
  --DB_PASSWORD=你的密码
```

### 方式 C：Docker 本地验证

```bash
cd G:\Premier-League-miniapp-server

# 设置必要变量（PowerShell）
$env:DB_PASSWORD="你的密码"
$env:FOOTBALL_DATA_TOKEN="你的Token"

# 构建并启动容器
docker-compose up --build
```

---

## 四、微信云托管 CloudBase Run 部署步骤

### Step 1：准备数据库

1. 登录 [微信云托管控制台](https://cloud.weixin.qq.com/cloudrun)
2. 选择你的小程序环境 → 进入「数据库」或直接购买 **TencentDB for MySQL**
3. 用 MySQL 客户端连接，执行：

```sql
CREATE DATABASE IF NOT EXISTS premierleague
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE premierleague;
SOURCE src/main/resources/db/schema.sql;
```

### Step 2：创建云托管服务

1. 控制台 → 新建服务
2. 服务名称：`premier-league-server`
3. 部署方式：**GitHub 仓库部署**
4. 选择仓库：`403938571Crist/Premier-League-miniapp-server`
5. 构建类型：**Dockerfile 构建**
6. 分支：`main`
7. 触发首次构建

### Step 3：配置环境变量

进入服务详情 →「版本配置」→「环境变量」，添加：

```yaml
DB_URL=jdbc:mysql://<TencentDB内网地址>:3306/premierleague?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=<你的数据库密码>
FOOTBALL_DATA_TOKEN=<你的 football-data Token>
```

> 不需要配置 Redis 相关变量。Dockerfile 已自带 `SPRING_PROFILES_ACTIVE=prod` 和 `JAVA_OPTS`。

### Step 4：实例配置（关键）

因服务包含 **Spring Scheduler 定时抓取任务**，必须保持至少 1 个常驻实例：

- 最小实例数：**1**
- 最大实例数：**3**（按流量调整）
- 容器规格：**0.25 核 0.5G 内存** 起步

### Step 5：发布并获取域名

部署成功后，云托管会分配 HTTPS 域名：

```
https://xxx.cloudrun.weixin.qq.com
```

将该域名填入小程序前端 `utils/env-config.js` 的 `PROD_BASE_URL` 中。

---

## 五、部署后验证清单

- [ ] `GET /` 返回 `{"status":"UP", ...}`
- [ ] `GET /health` 返回 200
- [ ] `GET /actuator/health/liveness` 返回 `{"status":"UP"}`
- [ ] `GET /actuator/health/readiness` 返回 `{"status":"UP"}`
- [ ] `GET /api/teams/standings` 正常返回积分榜 JSON
- [ ] 定时任务日志中出现 `[FootballDataSyncScheduler]` 同步记录

---

## 六、费用预估（个人项目/初创）

| 资源 | 配置 | 预估月费 |
|---|---|---|
| 云托管 | 0.25核 0.5G，最小实例 1 | ¥ 20 ~ 50 |
| TencentDB MySQL | 1核 1G，50G 存储 | ¥ 30 ~ 60 |
| Redis | **已移除，0 费用** | ¥ 0 |
| 流量 | 初创低流量 | 免费额度内 |
| **合计** | | **¥ 50 ~ 110 / 月** |

---

## 七、关键编译/构建验证

```bash
cd G:\Premier-League-miniapp-server
mvn clean package -DskipTests
```

- [x] `BUILD SUCCESS` 确认
- [x] 零 Redis 依赖（`pom.xml` 已删除）
- [x] 动态端口 + `0.0.0.0` 绑定
- [x] `application-prod.yml` 被打入 jar
