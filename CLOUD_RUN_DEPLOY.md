# 微信云托管部署指南

> 本文档说明如何将本 Spring Boot 项目部署到「微信云托管」，实现零运维运行。

---

## 前置条件

1. 已注册微信小程序账号（个人/企业均可）
2. 已开通「微信云托管」服务：https://cloud.weixin.qq.com/cloudrun
3. 已有可用的 MySQL 数据库
   - 推荐：在微信云托管控制台一键购买 **TencentDB for MySQL**（最便宜配置即可）
   - 备选：使用已有带公网 IP 的 MySQL 实例

---

## 一、配置数据库

### 1.1 创建数据库并导入表结构

```bash
mysql -h <你的MySQL地址> -u root -p

CREATE DATABASE IF NOT EXISTS premierleague
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE premierleague;
source src/main/resources/db/schema.sql;
```

### 1.2 确认数据库可被云托管访问

- 若使用 **TencentDB**：在云托管控制台绑定数据库，自动获得内网访问权限。
- 若使用 **外部 MySQL**：需在数据库安全组/防火墙中放行微信云托管的出口 IP（或开放 0.0.0.0/0 临时测试）。

---

## 二、准备代码（已完成）

项目根目录已包含以下文件：

- `Dockerfile` —— 用于构建云托管镜像
- `.dockerignore` —— 排除不需要打包的文件，加快构建速度

直接 push 到 GitHub 即可：

```bash
git add Dockerfile .dockerignore CLOUD_RUN_DEPLOY.md
git commit -m "Add WeChat Cloud Run deployment files"
git push
```

---

## 三、在云托管控制台部署

### 3.1 创建服务

1. 登录 [微信云托管控制台](https://cloud.weixin.qq.com/cloudrun)
2. 选择你的小程序环境 → 点击「新建服务」
3. 服务名称：`premier-league-server`
4. 部署方式：选择 **GitHub 仓库部署** 或 **本地上传代码包**
   - **推荐 GitHub**：授权后选择 `403938571Crist/Premier-League-miniapp-server`
5. 构建类型：选择 **Dockerfile 构建**
6. 发布：点击「开始部署」

### 3.2 配置环境变量

部署完成后，进入服务详情 →「服务配置」→「环境变量」，添加以下变量：

| 变量名 | 说明 | 示例 |
|---|---|---|
| `DB_URL` | MySQL 连接地址 | `jdbc:mysql://10.x.x.x:3306/premierleague?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `你的密码` |
| `FOOTBALL_DATA_TOKEN` | football-data.org API Token | `80e317eb06654d0e808e6b2021b19192` |
| `X_BEARER_TOKEN` | X (Twitter) API Token（可选） | `你的Token` |

> 💡 缓存已改为 Caffeine 本地缓存，部署时无需配置任何 Redis 环境变量。

### 3.3 实例配置（重要）

本服务包含**定时任务**（Spring Scheduler），需要至少 1 个常驻实例：

- 最小实例数：**1**
- 最大实例数：**3**（根据流量调整）
- 容器规格：**0.25 核 0.5G 内存**（起步够用，流量大了再升配）

设置路径：服务详情 →「版本配置」→「实例配置」。

### 3.4 获取公网访问域名

部署成功后，云托管会自动分配一个 HTTPS 域名：

```
https://premier-league-server-xxxxxx-xxx.env.run.tcloudbase.com
```

把这个域名记下来，下一步小程序里要用。

---

## 四、小程序端修改

打开小程序项目 `utils/api.js`，将 `baseUrl` 修改为云托管域名：

```javascript
const API_CONFIG = {
  baseUrl: 'https://你的云托管域名',
  apiKey: 'YOUR_API_KEY_HERE',
  competitionId: 'PL'
};
```

然后在微信小程序后台 →「开发管理」→「开发设置」→「服务器域名」→「request 合法域名」中添加该域名。

---

## 五、常见问题

### Q1：部署后定时任务没有执行？
A：请检查「最小实例数」是否设置为 1。如果实例数为 0，空闲时容器会被回收，定时任务就不会触发。

### Q2：数据库连接超时？
A：检查 MySQL 是否允许云托管的 IP 访问；如果使用 TencentDB，确认已绑定到正确的云开发环境。

### Q3：需要 Redis 吗？
A：**不需要**。项目已将 Redis 替换为 Caffeine 本地内存缓存（`CacheConfig.java`），微信云托管部署时无需购买和配置 Redis 实例。本地缓存已覆盖资讯、赛程、积分榜、球队、球员等全部缓存场景，并在 `FootballDataProvider` 中实现了 API 响应的本地缓存。

---

## 六、费用预估（个人项目/初创）

| 资源 | 配置 | 预估月费 |
|---|---|---|
| 云托管 | 0.25核 0.5G，最小1实例 | ¥ 20 ~ 50 |
| TencentDB MySQL | 1核 1G，50G 存储 | ¥ 30 ~ 60 |
| Redis | **已移除，0 费用** | ¥ 0 |
| 流量 | 初创低流量 | 免费额度内 |
| **合计** | | **¥ 50 ~ 110 / 月** |

> 注：新用户通常有免费额度，前几个月可能基本不花钱。
