# 英超小程序服务端

Spring Boot 后端服务，为微信小程序提供英超相关的数据接口。

## 技术栈

- Java 17
- Spring Boot 3
- MySQL 8
- Redis
- JPA / Hibernate

## 项目结构

```text
src/main/java/com/premierleague/server/
├── controller/      # 接口层
├── service/         # 业务层
├── repository/      # 数据访问层
├── entity/          # 实体定义
├── provider/        # 外部数据适配
└── config/          # 配置
```

## 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 7.0+

## 本地启动

### 1. 创建数据库

```bash
mysql -u root -p
CREATE DATABASE premierleague CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 启动服务

```bash
mvn -DskipTests spring-boot:run
```

服务默认监听：

```text
http://localhost:8080
```

## 健康检查

```bash
curl http://localhost:8080/api/admin/health
```

## 主要接口

### 资讯

```http
GET /api/news?page=1&pageSize=10
GET /api/news/{id}
GET /api/news/transfers
```

### 球队

```http
GET /api/teams
GET /api/teams/standings
GET /api/teams/{id}
GET /api/teams/{id}/squad
GET /api/teams/{id}/matches
GET /api/teams/{id}/stats
```

### 球员

```http
GET /api/players/{id}
GET /api/players/top-scorers
GET /api/players/top-assists
GET /api/social/players
```

## 说明

- 本仓库只保留对外需要的项目说明文档
- 其它内部设计、调试、部署、数据处理类文档默认仅保留在本地

