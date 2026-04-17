# 微信云托管部署指南

本文说明如何把 `G:\Premier-League-miniapp-server` 部署到微信云托管 CloudBase Run，并让小程序前端正确切到线上域名。

## 前置条件

1. 已开通微信云托管 CloudBase Run。
2. 已准备可访问的 MySQL 实例。
3. 已准备 `football-data.org` 的 API Token。
4. 小程序前端项目位于 `G:\Premier-League-miniapp-app`。

## 后端部署

项目已包含以下文件：

- `Dockerfile`
- `.dockerignore`

云托管构建方式请选择 `Dockerfile 构建`。

### 需要配置的环境变量

| 变量名 | 说明 | 示例 |
| --- | --- | --- |
| `PORT` | 云托管注入端口，本地可不填 | `8080` |
| `DB_URL` | MySQL JDBC 地址 | `jdbc:mysql://10.x.x.x:3306/premierleague?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | MySQL 用户名 | `root` |
| `DB_PASSWORD` | MySQL 密码 | `your-db-password` |
| `FOOTBALL_DATA_TOKEN` | football-data.org API Token | `your-football-data-token` |
| `X_BEARER_TOKEN` | X API Token，可选 | `your-x-token` |
| `SPRING_PROFILES_ACTIVE` | Spring 环境，可选 | `prod` |

### 健康检查

可用健康检查路径：

- `/`
- `/health`

### 实例建议

如果需要定时任务持续执行，最小实例数建议为 `1`。

## 前端配置

前端统一通过 `utils/env-config.js` 管理后端地址，不要再修改 `utils/api.js`。

文件：

- `G:\Premier-League-miniapp-app\utils\env-config.js`

当前关键配置示例：

```javascript
const PROD_BASE_URL = 'https://你的云托管域名/api';
const DEV_BASE_URL = 'http://localhost:8080/api';
const isProduction = MANUAL_PROD === null ? autoProd : MANUAL_PROD;
```

### 切换规则

- 正式版小程序：`envVersion === 'release'` 时自动使用 `PROD_BASE_URL`
- 开发版 / 体验版：默认使用 `DEV_BASE_URL`
- 如需临时强制切换：
  - `MANUAL_PROD = true` 强制生产环境
  - `MANUAL_PROD = false` 强制开发环境
  - `MANUAL_PROD = null` 恢复自动判断

`app.js` 会读取 `utils/env-config.js` 导出的 `API_BASE_URL`，`news`、`matches`、`standings`、`teams`、`players` 都共用这一后端地址。

### 小程序后台域名配置

在微信小程序后台：

`开发管理 -> 开发设置 -> 服务器域名 -> request 合法域名`

添加你的云托管 HTTPS 域名。

## 本地运行

### 后端

```bash
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

### 前端

开发环境默认请求：

```text
http://localhost:8080/api
```

## Docker 本地构建

```bash
docker build -t premier-league-server .
docker run --rm -p 8080:8080 \
  -e DB_URL="jdbc:mysql://host:3306/premierleague?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  -e DB_USERNAME="root" \
  -e DB_PASSWORD="your-db-password" \
  -e FOOTBALL_DATA_TOKEN="your-football-data-token" \
  premier-league-server
```

## 数据库初始化

如需手动初始化数据库：

```bash
mysql -h <your-mysql-host> -u root -p
CREATE DATABASE IF NOT EXISTS premierleague CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE premierleague;
source src/main/resources/db/schema.sql;
```
