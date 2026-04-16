#!/usr/bin/env python3
"""
英超资讯抓取演示脚本
抓取真实 RSS 源并展示结果
"""

import hashlib
import json
import re
import xml.etree.ElementTree as ET
from datetime import datetime
from urllib.request import urlopen, Request
from urllib.parse import urlencode
import ssl

# 忽略 SSL 验证
ssl._create_default_https_context = ssl._create_unverified_context

class NewsFetcher:
    """RSS 抓取器"""
    
    # RSS 源配置
    SOURCES = {
        'bbc': {
            'name': 'BBC Sport',
            'url': 'http://feeds.bbci.co.uk/sport/football/rss.xml',
            'type': 'official'
        },
        'espn': {
            'name': 'ESPN FC',
            'url': 'https://www.espn.com/espn/rss/soccer/news',
            'type': 'media'
        },
        'goal': {
            'name': 'Goal.com',
            'url': 'https://www.goal.com/feeds/en/news',
            'type': 'media'
        }
    }
    
    def __init__(self):
        self.results = []
    
    def fetch_rss(self, source_key):
        """抓取单个 RSS 源"""
        source = self.SOURCES.get(source_key)
        if not source:
            print(f"未知源: {source_key}")
            return []
        
        print(f"\n{'='*60}")
        print(f"正在抓取: {source['name']}")
        print(f"URL: {source['url']}")
        print(f"{'='*60}")
        
        try:
            # 设置请求头模拟浏览器
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            req = Request(source['url'], headers=headers)
            
            with urlopen(req, timeout=10) as response:
                data = response.read().decode('utf-8')
                
            # 解析 XML
            root = ET.fromstring(data)
            
            # RSS 2.0 格式
            items = root.findall('.//item')
            if not items:
                # Atom 格式
                items = root.findall('.//{http://www.w3.org/2005/Atom}entry')
            
            news_list = []
            for i, item in enumerate(items[:5]):  # 只取前5条
                news = self.parse_item(item, source_key, source)
                if news:
                    news_list.append(news)
                    self.print_news(news, i+1)
            
            return news_list
            
        except Exception as e:
            print(f"抓取失败: {e}")
            return []
    
    def parse_item(self, item, source_key, source):
        """解析单个 RSS Item"""
        try:
            # 提取标题
            title = item.findtext('title', '')
            if not title:
                title = item.findtext('.//{http://www.w3.org/2005/Atom}title', '')
            
            # 提取描述/摘要
            desc = item.findtext('description', '')
            if not desc:
                desc = item.findtext('.//{http://www.w3.org/2005/Atom}summary', '')
            
            # 清理 HTML
            desc = self.clean_html(desc)
            
            # 提取发布时间
            pub_date = item.findtext('pubDate', '')
            if not pub_date:
                pub_date = item.findtext('.//{http://www.w3.org/2005/Atom}published', '')
            
            # 解析日期
            source_published = self.parse_date(pub_date)
            
            # 提取链接
            link = item.findtext('link', '')
            if not link:
                link_elem = item.find('.//{http://www.w3.org/2005/Atom}link')
                if link_elem is not None:
                    link = link_elem.get('href', '')
            
            # 提取图片
            image = self.extract_image(item)
            
            # 生成指纹
            fingerprint = self.generate_fingerprint(title, source_key, source_published)
            
            # 检测媒体类型
            media_type = self.detect_media_type(title, desc)
            
            # 提取标签
            tags = self.extract_tags(title, desc)
            
            return {
                'id': fingerprint,
                'title': title[:200] if title else '无标题',
                'summary': desc[:500] if desc else title[:200],
                'source': source['name'],
                'sourceType': source_key,
                'mediaType': media_type,
                'sourcePublishedAt': source_published.isoformat() if source_published else datetime.now().isoformat(),
                'url': link,
                'coverImage': image,
                'tags': tags,
                'hotScore': self.calculate_hot_score(title, desc, source_key),
                'fingerprint': fingerprint,
                'fetchedAt': datetime.now().isoformat()
            }
            
        except Exception as e:
            print(f"解析条目失败: {e}")
            return None
    
    def clean_html(self, text):
        """清理 HTML 标签"""
        if not text:
            return ''
        # 移除 HTML 标签
        text = re.sub(r'<[^>]+>', '', text)
        # 移除多余空格
        text = re.sub(r'\s+', ' ', text)
        return text.strip()
    
    def parse_date(self, date_str):
        """解析日期字符串"""
        if not date_str:
            return datetime.now()
        
        formats = [
            '%a, %d %b %Y %H:%M:%S %z',
            '%Y-%m-%dT%H:%M:%SZ',
            '%Y-%m-%dT%H:%M:%S%z',
            '%a, %d %b %Y %H:%M:%S GMT'
        ]
        
        for fmt in formats:
            try:
                return datetime.strptime(date_str.strip(), fmt)
            except:
                continue
        
        return datetime.now()
    
    def extract_image(self, item):
        """提取图片 URL"""
        # 尝试各种方式提取图片
        media = item.find('.//{http://search.yahoo.com/mrss/}thumbnail')
        if media is not None:
            return media.get('url', '')
        
        media = item.find('.//{http://search.yahoo.com/mrss/}content')
        if media is not None:
            return media.get('url', '')
        
        # 从描述中提取
        desc = item.findtext('description', '')
        if desc:
            match = re.search(r'src=["\'](https?://[^"\']+\.(?:jpg|jpeg|png))["\']', desc, re.IGNORECASE)
            if match:
                return match.group(1)
        
        return None
    
    def generate_fingerprint(self, title, source_type, pub_date):
        """生成 MD5 指纹"""
        raw = f"{title}|{source_type}|{pub_date}"
        return hashlib.md5(raw.encode('utf-8')).hexdigest()
    
    def detect_media_type(self, title, content):
        """检测媒体类型"""
        if not title:
            return 'article'
        
        title_lower = title.lower()
        
        if any(kw in title_lower for kw in ['transfer', 'sign', 'deal', 'join']):
            return 'transfer'
        if any(kw in title_lower for kw in ['video', 'watch', 'highlights']):
            return 'video-summary'
        if len(content) < 200 if content else True:
            return 'quick'
        
        return 'article'
    
    def extract_tags(self, title, content):
        """提取标签"""
        tags = []
        text = (title + ' ' + (content or '')).lower()
        
        # 球队关键词
        teams = ['arsenal', 'man city', 'liverpool', 'man utd', 'chelsea', 
                'tottenham', 'newcastle', 'brighton', 'villa']
        
        for team in teams:
            if team in text:
                tags.append(team.replace(' ', '_'))
        
        # 类型关键词
        types = ['transfer', 'goal', 'injury', 'match', 'premier league']
        for t in types:
            if t in text:
                tags.append(t.replace(' ', '_'))
        
        return ','.join(tags[:5])  # 最多5个标签
    
    def calculate_hot_score(self, title, content, source_type):
        """计算热度值"""
        score = 50
        
        # 来源加权
        if source_type == 'bbc':
            score += 20
        elif source_type == 'espn':
            score += 15
        
        # 关键词加权
        if title:
            title_lower = title.lower()
            if 'transfer' in title_lower or 'sign' in title_lower:
                score += 15
            if any(t in title_lower for t in ['arsenal', 'man city', 'liverpool']):
                score += 10
        
        return min(score, 100)
    
    def print_news(self, news, index):
        """打印新闻信息"""
        print(f"\n[{index}] {news['title'][:60]}...")
        print(f"    来源: {news['source']} ({news['sourceType']})")
        print(f"    类型: {news['mediaType']}")
        print(f"    时间: {news['sourcePublishedAt']}")
        print(f"    热度: {news['hotScore']}")
        print(f"    指纹: {news['fingerprint'][:16]}...")
        print(f"    标签: {news['tags']}")
        print(f"    链接: {news['url'][:60]}...")
    
    def fetch_all(self):
        """抓取所有源"""
        all_news = []
        
        for source_key in self.SOURCES.keys():
            news_list = self.fetch_rss(source_key)
            all_news.extend(news_list)
        
        # 按时间排序
        all_news.sort(key=lambda x: x['sourcePublishedAt'], reverse=True)
        
        return all_news
    
    def save_to_json(self, news_list, filename='fetched_news.json'):
        """保存到 JSON 文件"""
        filepath = f"G:\\Premier League-app\\server\\scripts\\{filename}"
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(news_list, f, ensure_ascii=False, indent=2)
        print(f"\n[OK] 已保存 {len(news_list)} 条新闻到 {filepath}")


def main():
    print("="*70)
    print("Premier League News Fetcher Demo")
    print("="*70)
    
    fetcher = NewsFetcher()
    
    # 抓取所有源
    all_news = fetcher.fetch_all()
    
    print(f"\n{'='*70}")
    print(f"抓取完成！共 {len(all_news)} 条新闻")
    print(f"{'='*70}")
    
    # 统计
    source_stats = {}
    for news in all_news:
        st = news['sourceType']
        source_stats[st] = source_stats.get(st, 0) + 1
    
    print("\n来源统计:")
    for source, count in source_stats.items():
        print(f"  - {source}: {count} 条")
    
    # 保存到文件
    fetcher.save_to_json(all_news)
    
    # 生成 SQL
    print("\nGenerating SQL...")
    generate_sql(all_news)


def generate_sql(news_list):
    """生成 MySQL 插入语句"""
    sql_file = "G:\\Premier League-app\\server\\scripts\\insert_fetched.sql"
    
    with open(sql_file, 'w', encoding='utf-8') as f:
        f.write("USE premierleague;\n\n")
        f.write("-- 自动生成的抓取数据\n")
        f.write(f"-- 生成时间: {datetime.now().isoformat()}\n\n")
        
        for news in news_list:
            sql = f"""INSERT IGNORE INTO news (
    id, title, summary, source, source_type, media_type,
    source_published_at, url, cover_image, tags, hot_score,
    fingerprint, fetched_at
) VALUES (
    '{news['id']}',
    '{news['title'].replace("'", "''")[:500]}',
    '{news['summary'].replace("'", "''")[:2000]}',
    '{news['source']}',
    '{news['sourceType']}',
    '{news['mediaType']}',
    '{news['sourcePublishedAt']}',
    '{news['url'][:500]}',
    {f"'{news['coverImage'][:500]}'" if news['coverImage'] else 'NULL'},
    '{news['tags']}',
    {news['hotScore']},
    '{news['fingerprint']}',
    '{news['fetchedAt']}'
);\n"""
            f.write(sql)
        
        f.write(f"\n-- 共 {len(news_list)} 条\n")
    
    print(f"[OK] SQL saved to: {sql_file}")
    print("\nRun: mysql -u root -p premierleague < insert_fetched.sql")


if __name__ == '__main__':
    main()
