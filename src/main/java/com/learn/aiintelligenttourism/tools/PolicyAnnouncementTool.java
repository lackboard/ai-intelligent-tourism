package com.learn.aiintelligenttourism.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PolicyAnnouncementTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> BUREAU_NOTICE_URLS = new LinkedHashMap<>() {{
        put("北京", "https://whlyj.beijing.gov.cn/");
        put("上海", "https://whlyj.sh.gov.cn/");
        put("广东", "https://whly.gd.gov.cn/");
        put("四川", "https://wlt.sc.gov.cn/");
        put("浙江", "https://ct.zj.gov.cn/");
        put("江苏", "https://wlt.jiangsu.gov.cn/");
        put("陕西", "http://whhlyt.shaanxi.gov.cn/");
        put("重庆", "https://whlyw.cq.gov.cn/");
        put("云南", "https://dct.yn.gov.cn/");
    }};

    private static final Map<String, String> SCENIC_NOTICE_URLS = new LinkedHashMap<>() {{
        put("故宫", "https://www.dpm.org.cn/");
        put("兵马俑", "https://www.bmy.com.cn/");
        put("黄山", "https://www.huangshan.com.cn/");
        put("九寨沟", "https://www.jiuzhai.com/");
        put("峨眉山", "https://www.ems517.com/");
        put("武隆", "http://www.wlkst.com/");
        put("西湖", "https://www.westlake.com.cn/");
        put("鼓浪屿", "https://www.glysyw.com/");
    }};

    @Tool(description = "抓取省市文旅局官网公告入口，返回官方链接及与关键词相关的公告链接。")
    public String fetchCultureTourismBureauNotices(
            @ToolParam(description = "省或市名称，如 北京、四川、上海") String region,
            @ToolParam(description = "可选关键词，如 预约、限流、闭园") String keyword
    ) {
        String officialUrl = resolveUrl(BUREAU_NOTICE_URLS, region);
        if (officialUrl == null) {
            return String.format("{\"error\":\"未找到该地区文旅局官网映射: %s\",\"suggestion\":\"请提供常见省市名，例如北京、上海、四川\"}", region);
        }
        return crawlNoticePage("bureau_notice", region, officialUrl, keyword);
    }

    @Tool(description = "抓取重点景区官方公告页入口，返回官方链接及与关键词相关的公告链接。")
    public String fetchScenicOfficialNotices(
            @ToolParam(description = "景区名称，如 故宫、兵马俑、九寨沟") String scenicName,
            @ToolParam(description = "可选关键词，如 开放时间、预约入口、门票") String keyword
    ) {
        String officialUrl = resolveUrl(SCENIC_NOTICE_URLS, scenicName);
        if (officialUrl == null) {
            return String.format("{\"error\":\"未找到该景区官网映射: %s\",\"suggestion\":\"可先询问重点景区，例如故宫、兵马俑、九寨沟\"}", scenicName);
        }
        return crawlNoticePage("scenic_notice", scenicName, officialUrl, keyword);
    }

    private String crawlNoticePage(String sourceType, String subject, String officialUrl, String keyword) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("source_type", sourceType);
        root.put("subject", subject == null ? "" : subject);
        root.put("official_url", officialUrl);
        root.put("fetched_at", OffsetDateTime.now().toString());
        root.put("keyword", keyword == null ? "" : keyword.trim());

        try {
            Document document = Jsoup.connect(officialUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            root.put("page_title", document.title());
            ArrayNode notices = root.putArray("notices");

            String host = URI.create(officialUrl).getHost();
            Elements links = document.select("a[href]");
            int limit = 8;
            for (Element link : links) {
                if (limit <= 0) {
                    break;
                }
                String title = link.text() == null ? "" : link.text().trim();
                String href = link.absUrl("href");
                if (title.length() < 4 || href.isBlank()) {
                    continue;
                }
                if (keyword != null && !keyword.isBlank() && !title.contains(keyword.trim())) {
                    continue;
                }
                if (!isLikelyOfficialLink(host, href)) {
                    continue;
                }

                ObjectNode item = notices.addObject();
                item.put("title", title);
                item.put("url", href);
                limit--;
            }

            if (notices.isEmpty()) {
                root.put("message", "未命中关键词公告，已返回官网入口，请以官网公告页为准。");
            }
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            root.put("error", "抓取公告页失败: " + e.getMessage());
            root.put("message", "请访问 official_url 查看最新公告。");
            return root.toString();
        }
    }

    private String resolveUrl(Map<String, String> mapping, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (trimmed.contains(entry.getKey()) || entry.getKey().contains(trimmed)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isLikelyOfficialLink(String baseHost, String href) {
        try {
            String host = URI.create(href).getHost();
            if (host == null || baseHost == null) {
                return true;
            }
            return host.equals(baseHost)
                    || host.endsWith("." + baseHost)
                    || baseHost.endsWith("." + host)
                    || host.endsWith(".gov.cn");
        } catch (Exception e) {
            return false;
        }
    }
}

