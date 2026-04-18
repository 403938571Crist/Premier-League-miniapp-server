# 本地开发环境备忘

## MySQL 8.0.45（绿色版，非服务）

**安装位置**：`C:\Users\Crist\tools\mysql-8.0.45-winx64\`
**配置文件**：`C:\Users\Crist\tools\mysql-8.0.45-winx64\my.ini`
**数据目录**：`C:\Users\Crist\tools\mysql-data\`
**临时目录**：`C:\Users\Crist\tools\mysql-tmp\`
**PID/日志目录**：`C:\Users\Crist\tools\mysql-run\`
- `mysqld.pid`（进程 ID 文件）
- `mysqld.err`（错误日志）

**端口**：3306 (bind 127.0.0.1)
**字符集**：utf8mb4 / utf8mb4_0900_ai_ci
**用户名**：root（无密码，`DB_PASSWORD` 环境变量为空时生效）
**数据库**：`premierleague`

### 启动命令

```bash
"C:/Users/Crist/tools/mysql-8.0.45-winx64/bin/mysqld.exe" \
    --defaults-file="C:/Users/Crist/tools/mysql-8.0.45-winx64/my.ini" \
    --console &
```

启动后 5~6 秒内可以接受连接。用 `mysql -u root -h 127.0.0.1 -e "SELECT VERSION();"` 验证。

### 停止命令

```bash
powershell -Command "Get-Process mysqld | Stop-Process -Force"
```

### 注意事项

- MySQL **没有注册为 Windows 服务**，机器重启后需要手动启动
- 以前我（Claude）误以为你手动装的，其实是我之前装的，要我启时直接用上面命令即可

---

## Spring Boot 服务

**项目根目录**：`G:\Premier-League-miniapp-server\`
**端口**：8080
**数据库配置**：`application.yml` 中 `datasource.url` 连 `localhost:3306/premierleague`，用户 `root` 无密码

### 启动

```bash
cd "G:/Premier-League-miniapp-server"
mvn -DskipTests spring-boot:run > target/server.log 2>&1 &
```

### 停止

```bash
# 找 8080 端口的进程
powershell -Command "Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -ExpandProperty OwningProcess"
# 然后 Stop-Process -Id <pid> -Force
```

### 健康检查

```bash
curl http://localhost:8080/api/admin/health
```

### 常用管理端点

| 端点 | 说明 |
|---|---|
| `POST /api/admin/fetch/{frequency}` | 手动触发抓取（high/medium/low） |
| `POST /api/admin/fetch/source/{sourceType}` | 手动抓单个源 |
| `POST /api/admin/backfill/dongqiudi?limit=50` | 懂球帝正文回填（对 content 为空的文章） |
| `GET /api/admin/stats` | 抓取统计 |

---

## 小程序前端

**项目根目录**：`G:\Premier-League-miniapp-app\`

用微信开发者工具打开即可，接口基址在 `utils/env-config.js`。

---

## 产品/工程决策备忘

### 射手榜 / 助攻榜：保留入口，不上 mock

位置：`pages/standings/standings.js` 里的 `TAB_CONFIG.SCORERS` / `TAB_CONFIG.ASSISTS`

- **策略**：后端目前没有射手榜/助攻榜的 `/api`，**不造假数据、不用 mock**。
- **表现**：tab 入口保留在 `TAB_ORDER` 里（TOTAL / SCORERS / ASSISTS 都展示），切到 SCORERS/ASSISTS 显示 `.placeholder-card`，明确告知"暂未接入正式接口 / 待后端正式接口接入后展示"。
- **规则**：后续如果改动这两个 tab，**不要加 mock**——要么等正式 `/api`，要么保持当前 placeholder 状态。
- **文案源**：`TAB_CONFIG[*].description`（顶部 hero 卡）+ `emptyTitle` + `emptyDescription`（placeholder 卡）
- **数据加载分支**：`loadData()` 里只有 TOTAL 会调 `getStandings()`；切到其它 tab 不发请求。

### 资讯详情本地化（2026-04-17 实装）

- 懂球帝详情**不走 H5/WebView**，后端抓 PC 版 `https://www.dongqiudi.com/articles/{id}.html` 解析 `div.con > div[style*=display:none]` 拿段落 + 图片，写入 `news.content`
- 格式约定：段落用 `\n\n` 分隔，图片写成 `[IMG:url]` 占位
- `NewsService.parseBlocks()` 按 `\n\n` 切块，识别 `[IMG:]` 前缀发射 `ArticleBlock.image(url)`；前端 `news-detail.wxml` 有 `item.type === 'image'` 分支用 `<image mode="widthFix" lazy-load>` 渲染
- 批量回填旧数据：`POST /api/admin/backfill/dongqiudi?limit=50`（只处理 content 为空的行）
- 其它源（official / sky / guardian / reddit / romano）**还没本地化**，详情页走 summary 或 WebView 兜底。下一轮工作可以考虑把 RSS description 写入 content。
