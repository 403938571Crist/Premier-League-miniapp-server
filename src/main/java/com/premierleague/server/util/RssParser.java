package com.premierleague.server.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RSS 解析工具
 */
@Slf4j
public class RssParser {
    
    /**
     * 解析 RSS XML
     */
    public static List<Map<String, String>> parse(String xml) {
        List<Map<String, String>> items = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            
            // 尝试 RSS 2.0 格式
            NodeList itemNodes = doc.getElementsByTagName("item");
            
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element item = (Element) itemNodes.item(i);
                Map<String, String> data = new HashMap<>();
                
                data.put("title", getTextContent(item, "title"));
                data.put("description", getTextContent(item, "description"));
                data.put("link", getTextContent(item, "link"));
                data.put("pubDate", getTextContent(item, "pubDate"));
                data.put("author", getTextContent(item, "author"));
                
                // 尝试提取图片
                String image = extractImage(item);
                if (image != null) {
                    data.put("image", image);
                }
                
                items.add(data);
            }
            
        } catch (Exception e) {
            log.error("RSS parse failed", e);
        }
        
        return items;
    }
    
    private static String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }
    
    private static String extractImage(Element item) {
        // 尝试 media:thumbnail
        NodeList thumbnails = item.getElementsByTagName("media:thumbnail");
        if (thumbnails.getLength() > 0) {
            return ((Element) thumbnails.item(0)).getAttribute("url");
        }
        
        // 尝试 media:content
        NodeList contents = item.getElementsByTagName("media:content");
        if (contents.getLength() > 0) {
            return ((Element) contents.item(0)).getAttribute("url");
        }
        
        // 从 description 中提取
        String desc = getTextContent(item, "description");
        if (desc.contains("src=\"")) {
            int start = desc.indexOf("src=\"") + 5;
            int end = desc.indexOf("\"", start);
            if (end > start) {
                return desc.substring(start, end);
            }
        }
        
        return null;
    }
    
    /**
     * 解析 RSS 日期格式
     */
    public static LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return LocalDateTime.now();
        }
        
        // 尝试多种格式
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        };
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDateTime.parse(dateStr.trim(), formatter);
            } catch (Exception e) {
                // 尝试下一个格式
            }
        }
        
        return LocalDateTime.now();
    }
}
