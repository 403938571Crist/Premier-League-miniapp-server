# 资讯源接入指南

## 源状态一览

| 源 | 类型 | 频率 | 状态 | 说明 |
|----|------|------|------|------|
| **Fabrizio Romano** | `romano` | 高频(2min) | 🔴 待配置 | 需 X API Bearer Token |
| **X (Twitter)** | `x` | 高频(2min) | 🔴 待配置 | 需 X API Bearer Token |
| **懂球帝** | `dongqiudi` | 中频(10min) | 🟡 Mock数据 | 框架已搭建，API待接入 |
| **英超官方** | `official` | 中频(10min) | 🟡 Mock数据 | 框架已搭建，API待接入 |

### 预留扩展源（低频）

| 源 | 类型 | 频率 | 状态 | 说明 |
|----|------|------|------|------|
| **Bilibili** | `bilibili` | 低频(30min) | 🔴 预留扩展 | 仅预留扩展位，未实现 |
| **抖音** | `douyin` | 低频(30min) | 🔴 预留扩展 | 仅预留扩展位，未实现 |

> **注意**: 当前所有资讯源均返回 Mock 数据，用于前端联调和UI展示。真实抓取需后续接入各平台API。

---

## 高频源配置 (罗马诺 & X)

### 1. 申请 X API Key

1. 访问 https://developer.twitter.com/
2. 创建 Project 和 App
3. 生成 Bearer Token

### 2. 配置环境变量

```bash
# Windows PowerShell
$env:X_BEARER_TOKEN="你的BearerToken"

# Linux/Mac
export X_BEARER_TOKEN="你的BearerToken"
```

或在 `application.yml` 中配置：
```yaml
app:
  x:
    bearer-token: "你的BearerToken"
```

### 3. 验证配置

```bash
# 检查源可用性
curl http://localhost:8080/api/admin/sources

# 手动触发X源抓取
curl -X POST http://localhost:8080/api/admin/fetch/source/x
```

---

## 中频源

### 懂球帝
- **当前状态**: Mock数据
- **目标API**: `https://www.dongqiudi.com/api/app/tabs/web/56.json`
- **栏目ID**: 56 (英超)
- **特点**: 中文资讯，覆盖全面
- **待实现**: 真实API接入、英超内容过滤

### 英超官方
- **当前状态**: Mock数据
- **目标**: 接入英超官方数据API或 RSS
- **待实现**: 官方API申请、RSS解析

---

## 预留扩展源（低频）

### Bilibili
- **状态**: 预留扩展位，未实现
- **潜在API**: 搜索接口
- **关键词**: 英超、阿森纳、曼城、利物浦等
- **难点**: B站API需要认证，反爬机制较强

### 抖音
- **状态**: 预留扩展位，未实现
- **难点**: 需要签名算法或官方API权限

---

## 手动触发抓取

```bash
# 按频率触发
curl -X POST http://localhost:8080/api/admin/fetch/high
curl -X POST http://localhost:8080/api/admin/fetch/medium
curl -X POST http://localhost:8080/api/admin/fetch/low

# 按源触发（当前返回Mock数据）
curl -X POST http://localhost:8080/api/admin/fetch/source/dongqiudi
curl -X POST http://localhost:8080/api/admin/fetch/source/official
```

---

## 添加新源

参考 `DongqiudiProvider.java` 实现 `NewsProvider` 接口：

```java
@Component
@RequiredArgsConstructor
public class NewProvider implements NewsProvider {
    
    private final HttpClientUtil httpClient;
    private final ContentCleanService contentCleanService;
    
    @Override
    public List<News> fetchLatest(int maxItems) {
        // 1. 调用API或解析网页
        String response = httpClient.get("https://api.example.com/news");
        
        // 2. 解析数据
        List<News> newsList = parse(response);
        
        // 3. 返回
        return newsList;
    }
    
    @Override
    public String getSourceType() { return "example"; }
    
    @Override
    public String getFrequencyLevel() { return "medium"; }
}
```

然后在 `application.yml` 配置频率：
```yaml
app:
  fetch:
    medium-frequency:
      sources: [..., example]
```
