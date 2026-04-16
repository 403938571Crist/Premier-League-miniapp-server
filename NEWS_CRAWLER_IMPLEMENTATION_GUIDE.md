# 英超资讯爬虫实现说明

这份文档只讲 `资讯 / 社媒 / 转会消息` 的采集实现。

不要把这份文档用于：

- 赛程
- 积分榜
- 球队资料
- 球员基础资料

这些结构化数据仍然优先走正式 API。

---

## 1. 爬虫范围先定死

第一阶段只做这些源：

1. 英超官方资讯
2. Fabrizio Romano 转会资讯
3. X 上的球员社媒主页信息
4. 懂球帝英超资讯
5. 可选补充媒体源：BBC / The Guardian / ESPN FC

第一阶段不要做：

- B站
- 抖音
- Selenium 浏览器自动化
- 截图识别
- 反爬对抗

原因：

- B站 / 抖音内容太重，不适合当前前端展示
- Selenium 成本高、稳定性差、维护重
- 现在目标是先把“稳定可用的数据链路”跑通

---

## 2. 总体原则

资讯源处理顺序：

1. RSS
2. 公开 JSON 接口
3. 静态 HTML 抓取
4. 最后才考虑复杂动态网页

也就是说：

- 能用 RSS 就不要先抓 HTML
- 能用公开 JSON 就不要先写 Jsoup
- 不能稳定抓到时，返回空结果，不要造 mock

---

## 3. 每类源怎么做

### 3.1 英超官方资讯

优先方案：

- RSS

建议实现：

1. 先抓 RSS
2. 解析标题、摘要、链接、发布时间
3. 再过滤出英超相关内容

Provider 建议：

```java
OfficialNewsProvider
```

实现建议：

- 用 `HttpClientUtil` 拉 RSS XML
- 用标准 XML parser 解析 `item`
- 不要自己正则抠 XML

抓取结果至少映射这些字段：

- `title`
- `summary`
- `source = Premier League`
- `sourceType = official`
- `mediaType = article`
- `sourcePublishedAt`
- `url`
- `coverImage`
- `tags`

注意：

- 官方 RSS 不稳定时，可以退到英超官网资讯列表页 HTML
- 但 HTML 抓取只能做 fallback，不要一开始主逻辑就写成抓网页

---

### 3.2 Fabrizio Romano 转会资讯

优先方案：

1. X API
2. RSS 镜像 / RSSHub / Nitter 类 RSS 源

Provider 建议：

```java
RomanoNewsProvider
```

抓取目标：

- 标题
- 摘要
- 原文链接
- 发布时间
- 来源作者
- 转会标签
- 关联球队 / 关联球员

实现建议：

- 如果有 X bearer token，优先走 X API
- 没有 token 时再尝试 RSS fallback
- 不要写死单一镜像地址，RSS 源建议做列表轮询

特别注意：

- `from:` 查询不要用 numeric user id
- 应该用 `from:FabrizioRomano` 这种用户名

文本处理建议：

- 标题：正文前 80~100 字
- 摘要：正文前 150~220 字
- `Here we go`、`deal agreed`、`medical`、`loan`、`transfer` 等词可以提高热度

---

### 3.3 X 球员社媒

这一块第一阶段不要抓“全部动态流”。

第一阶段只做：

- 球员社媒主页链接
- 平台类型
- 账号名
- 展示名
- 头像
- 个人简介

Provider 建议：

```java
PlayerSocialProfileProvider
```

为什么先做主页信息而不是全文动态：

- 风险更低
- 结构更稳定
- 对小程序前端已经够用
- 可以在“球员详情”里先展示“社媒入口”

如果后面真要抓动态，再单独做：

```java
PlayerSocialPostProvider
```

但这不是第一阶段必需。

---

### 3.4 懂球帝资讯

优先方案：

1. 公开 JSON 接口
2. HTML 列表页抓取

Provider 建议：

```java
DongqiudiNewsProvider
```

建议流程：

1. 先找稳定的栏目 JSON
2. 拉列表
3. 拿文章 id / 链接
4. 按需抓详情页正文

第一阶段不要把懂球帝做得太重：

- 列表页可只拿标题、摘要、封面、发布时间
- 详情页可以只保留跳转链接或摘要块

不要一开始就尝试全站正文富文本还原。

---

### 3.5 补充媒体源

建议只选 1~2 个稳定英文源：

- BBC Sport Football
- The Guardian Football
- ESPN FC

这些源第一阶段只要做：

- 列表抓取
- 简要摘要
- 跳转原文

用途：

- 丰富资讯流
- 给前端多源展示
- 在官方源和罗马诺源之间补内容密度

---

## 4. 不同源的抓取方式

### 4.1 RSS 源

适用：

- 英超官方
- Romano fallback
- BBC / Guardian 等

实现方式：

- `HttpClientUtil.get(url)`
- XML parser 解析
- 每个 item 映射成统一 `News`

不要做：

- 正则解析 XML

---

### 4.2 JSON 接口源

适用：

- 懂球帝
- X API

实现方式：

- `HttpClientUtil.get(...)` 或 `getWithAuth(...)`
- `ObjectMapper` 解析 JSON
- 建立单独 response model 或内部 parser

不要做：

- 直接把第三方 JSON 原样透传给前端

---

### 4.3 HTML 源

适用：

- RSS 不可用时的官网列表页
- 个别资讯列表页

实现方式建议：

- Java 用 `Jsoup`

抽取最小必要字段：

- 标题
- 摘要
- 链接
- 封面图
- 发布时间

不要第一阶段就抓：

- 评论
- 点赞
- 全量正文样式
- 嵌入视频

---

## 5. 统一数据模型

无论哪个源，服务端内部都统一映射到 `News`。

最少字段：

- `id`
- `title`
- `summary`
- `source`
- `sourceType`
- `mediaType`
- `sourcePublishedAt`
- `url`
- `coverImage`
- `author`
- `tags`
- `hotScore`
- `fingerprint`
- `fetchedAt`
- `fetchBatchId`

如果是详情页数据，再补：

- `content`
- `sourceNote`
- `relatedTeamIds`
- `relatedPlayerIds`

---

## 6. 去重规则

同一条资讯不要因为多个源重复灌进库里。

建议规则：

### 一级去重

- `source + url`

### 二级去重

- `md5(normalizedTitle + sourceType + sourcePublishedAt)`

### 三级近似去重

- 标题去噪后相似度比对

第一阶段至少做前两级。

---

## 7. 抓取频率

### 高频源

- Romano
- X 快讯

频率：

- `2~5 分钟`

### 中频源

- 官方资讯
- 懂球帝
- 英文媒体

频率：

- `10~20 分钟`

### 低频源

- 球员社媒主页

频率：

- `6~24 小时`

注意：

- 抓取调度不要全写死在 `@Scheduled`
- 频率最好可配置

---

## 8. Redis 与 MySQL 分工

### Redis

负责：

- 首页资讯列表缓存
- 转会快讯缓存
- 详情页缓存
- 球员社媒列表缓存

### MySQL

负责：

- 正式落库
- 去重主存储
- 历史查询
- 抓取日志

建议缓存 TTL：

- `newsList`：5 分钟
- `newsDetail`：30 分钟
- `transferNews`：2 分钟
- `socialPlayers`：6 小时

---

## 9. Provider 代码怎么写

接口建议保持现在这套：

```java
public interface NewsProvider {
    String getSourceType();
    String getSourceName();
    String getFrequencyLevel();
    boolean isAvailable();
    List<News> fetchLatest(int maxItems);
}
```

每个 provider 内部结构建议统一：

1. `fetchLatest`
2. `fetchFromApi / fetchFromRss / fetchFromHtml`
3. `parseResponse`
4. `mapToNews`
5. `isRelevant`

不要在一个方法里把全部逻辑塞完。

---

## 10. 详情页怎么做

前端已经需要：

- 图文资讯卡
- 点击进入详情页

所以服务端要支持：

### 列表接口

```http
GET /api/news
```

返回：

- 标题
- 摘要
- 封面
- 来源
- 发布时间
- sourceType
- url

### 详情接口

```http
GET /api/news/{id}
```

返回：

- 标题
- 来源
- 发布时间
- 封面
- blocks
- 原文链接
- 来源说明

注意：

- 如果源只适合列表展示，详情页可以返回“摘要 + 原文链接”
- 不要求每个源第一阶段都完整抓正文

---

## 11. 错误处理

### 源不可用时

- 返回空列表
- 记录日志
- 不抛到 controller 直接炸接口

### 单条解析失败时

- 跳过当前 item
- 继续处理剩余数据

### 抓取失败时

- 记录到 `fetch_log`
- 标记 source、batchId、异常摘要、耗时

不要做：

- 源失败后自动造 mock 数据

---

## 12. Kimi 第一阶段实施清单

请按顺序做：

1. 把 `OfficialProvider` 改成 RSS/官网资讯真实抓取
2. 把 `RomanoProvider` 改成 X API + RSS fallback
3. 实现 `DongqiudiProvider` 的真实列表抓取
4. 新增 `PlayerSocialProfileProvider`
5. 统一 `News` 映射和去重
6. 接好 Redis 缓存
7. 完善 `GET /api/news`
8. 完善 `GET /api/news/{id}`
9. 完善 `GET /api/news/transfers`
10. 完善 `GET /api/social/players`

---

## 13. 明确不要做的事

不要：

- 再写 mock 新闻数据
- 前端直连第三方资讯源
- 一开始就接 B站 / 抖音
- 一开始就做 Selenium
- 一开始就抓动态评论和富文本全量还原

先把：

- 英超官方
- 罗马诺
- X
- 懂球帝

这几条真实链路跑通。

---

## 14. 可以直接发给 Kimi 的话

```text
请按 NEWS_CRAWLER_IMPLEMENTATION_GUIDE.md 实现资讯采集。

要求：
1. 这份文档只用于资讯/社媒/转会消息，不用于赛程和积分榜
2. 不要 mock 数据
3. 不要先接 B站/抖音
4. 优先级：RSS > JSON 接口 > HTML 抓取
5. 先完成 OfficialProvider、RomanoProvider、DongqiudiProvider、PlayerSocialProfileProvider
6. 前端需要图文资讯卡和详情页，所以 /api/news 和 /api/news/{id} 必须可用
7. 失败时返回空结果并记日志，不要造假数据
```
