# 懂球帝 Provider 整改说明

这份文档只解决一件事：

`DongqiudiProvider` 现在虽然接了真实接口，但还不能稳定作为英超资讯源使用，需要按下面的方式整改。

---

## 1. 当前问题总结

### 1.1 栏目抓错

当前代码使用：

```text
https://www.dongqiudi.com/api/app/tabs/web/56.json?page=1
```

但这个接口当前返回的 `label` 是：

```text
中超
```

所以现在抓到的是中超新闻，不是英超新闻。

这意味着：

- 首页“英超资讯”会混入中超内容
- 后续详情页也会全部跑偏

---

### 1.2 时间字段不能直接用 `published_at`

当前接口里部分数据会出现：

- `published_at = 2027 / 2029 / 2030`
- `created_at = 2026-04-12 ...`

因此不能直接把 `published_at` 当成最终发布时间。

否则会导致：

- 首页排序错误
- 去重指纹受污染
- 热门列表被未来时间顶上去

---

### 1.3 只抓列表，不抓详情

当前 `DongqiudiProvider` 只抓了栏目列表。

但列表里很多文章：

- `description = ""`
- `b_description = ""`

最后只能回退成“标题即摘要”。

这会直接导致：

- `/api/news` 只有标题，没有像样摘要
- `/api/news/{id}` 没有正文 blocks
- 前端图文详情页没有真实内容

---

### 1.4 媒体类型判断不可靠

现在代码是这样判断：

```java
int type = article.path("type").asInt();
```

但这个列表接口更稳定的字段其实是：

- `channel`
- `showtype`
- `is_video`
- `template`

所以媒体类型识别会失真。

---

## 2. 正确实现目标

`DongqiudiProvider` 第一阶段要达到这 3 个目标：

1. 列表接口只返回英超相关内容
2. 详情接口能补到真实正文或正文块
3. 时间、封面、媒体类型都要尽量稳定

---

## 3. 正确实现方案

### 3.1 第一步：先确认英超栏目，不要继续写死 `56`

要求：

- 不要继续假设 `56 = 英超`
- 先找到真正的英超栏目源

建议做法：

1. 调研懂球帝栏目 JSON 或其他公开入口
2. 找到能稳定返回英超内容的栏目 ID 或列表接口
3. 在代码里把栏目 ID 抽成配置，不要写死在类里

建议改成：

```java
@Value("${app.sources.dongqiudi.premier-league-tab-id}")
private String premierLeagueTabId;
```

或者：

```java
@Value("${app.sources.dongqiudi.list-url}")
private String listUrl;
```

这样后面栏目变化时不需要改代码。

---

### 3.2 第二步：列表接口只做“轻列表抓取”

列表阶段只抽这些字段：

- `id`
- `title`
- `description`
- `b_description`
- `thumb`
- `created_at`
- `sort_timestamp`
- `channel`
- `showtype`
- `is_video`
- `template`
- `author_name`
- `url`

列表阶段目标：

- 提供 `/api/news` 的资讯列表
- 提供详情页基础索引

不要在列表阶段就试图把正文一次性补全。

---

### 3.3 第三步：详情页单独补抓

对于前端点击进入详情页，需要补一条“详情抓取链路”。

建议新增：

```java
DongqiudiDetailProvider
```

或在现有 provider 中增加：

```java
NewsDetail fetchDetail(String articleId)
```

详情抓取逻辑建议：

1. 先尝试公开 JSON / 详情接口
2. 如果拿不到，再抓文章详情 HTML
3. 解析正文段落、图片、引用
4. 转成统一 `blocks`

最少需要支持：

- 标题
- 来源
- 发布时间
- 封面
- 段落文本
- 图片
- 原文链接

---

## 4. 时间字段正确处理方式

### 4.1 时间优先级

懂球帝列表数据建议按这个优先级取时间：

1. `created_at`
2. `sort_timestamp`
3. `published_at`
4. `LocalDateTime.now()`

推荐实现逻辑：

```java
if (createdAt not empty) {
    use createdAt;
} else if (sortTimestamp exists) {
    use sortTimestamp;
} else if (publishedAt looks normal) {
    use publishedAt;
} else {
    use now;
}
```

### 4.2 不要信未来时间

如果某个时间明显超过当前时间很多，比如：

- 晚于当前系统时间 24 小时以上

则判定为异常时间，不直接采用。

---

## 5. 摘要字段正确处理方式

摘要优先级建议：

1. `description`
2. `b_description`
3. 详情页正文前 120~180 字
4. 标题

不要直接无脑：

```java
summary = title
```

这只能当最后兜底。

---

## 6. 媒体类型正确处理方式

建议按这套规则判断：

### 6.1 视频

满足任一：

- `is_video = true`
- `channel = video`
- `showtype = video`

映射为：

```text
video-summary
```

### 6.2 图集 / feed

满足任一：

- `showtype = feed`
- `template = top.html`
- 存在 `mini_top_content.images`

映射为：

```text
gallery
```

### 6.3 普通文章

其他情况：

```text
article
```

---

## 7. 英超过滤建议

即使找到了英超栏目，也建议再做一次轻过滤。

建议保留这些关键词：

- 英超
- 阿森纳
- 利物浦
- 曼城
- 曼联
- 切尔西
- 热刺
- 纽卡
- 布莱顿
- 维拉
- 埃弗顿
- 富勒姆
- 布伦特福德
- 伯恩茅斯
- 西汉姆
- 狼队
- 水晶宫
- 诺丁汉森林
- 桑德兰

但不要像现在这样只过滤“垃圾内容”，不校验业务相关性。

---

## 8. 数据库存储建议

列表抓取入库时，至少保证这些字段完整：

- `id`
- `title`
- `summary`
- `source = 懂球帝`
- `sourceType = dongqiudi`
- `mediaType`
- `sourcePublishedAt`
- `author`
- `coverImage`
- `url`
- `hotScore`
- `fingerprint`
- `fetchedAt`
- `fetchBatchId`

详情补抓成功后，再补：

- `content`
- `sourceNote`

不要要求列表抓取一次把全部内容写满。

---

## 9. 建议拆分实现

### 9.1 列表 Provider

```java
DongqiudiProvider
```

职责：

- 拉列表
- 轻解析
- 入库基础资讯

### 9.2 详情抓取 Service

```java
DongqiudiDetailService
```

职责：

- 按文章 id 或 url 抓详情
- 解析正文
- 更新 `content`
- 生成详情 blocks

这样职责更清晰。

---

## 10. 给 Kimi 的直接要求

可以直接发给 Kimi：

```text
请按 DONGQIUDI_PROVIDER_FIX_GUIDE.md 整改懂球帝接入。

要求：
1. 不要再用 tab 56 当英超栏目，先确认真正的英超源
2. 时间字段优先使用 created_at 或 sort_timestamp，不要直接信 published_at
3. 列表和详情分开处理
4. /api/news 列表可先用轻列表抓取
5. /api/news/{id} 必须补详情抓取，不要只靠列表 description
6. mediaType 改用 channel/showtype/is_video/template 判断
7. 最终只保留英超相关内容
```
