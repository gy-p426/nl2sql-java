package com.nl2sql.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 解析工具
 */
@Slf4j
@Component
public class JsonParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从响应文本中提取 JSON
     */
    public Map<String, Object> extractJsonFromResponse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // 预处理：清理格式
        String cleaned = text.replaceAll("[\\r\\n\\t]", " ")
                            .replaceAll("\\s+", " ");

        // 使用状态机解析 JSON 边界
        int braceCount = 0;
        int startIdx = -1;

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{') {
                if (startIdx == -1) {
                    startIdx = i;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && startIdx != -1) {
                    String jsonStr = cleaned.substring(startIdx, i + 1);
                    try {
                        return objectMapper.readValue(jsonStr, Map.class);
                    } catch (Exception e) {
                        // 继续尝试下一个
                    }
                }
            }
        }

        // 回退方法：正则表达式
        return fallbackExtraction(text);
    }

    /**
     * 回退提取方法
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fallbackExtraction(String text) {
        String[] patterns = {
            "\\{\\s*\"keywords_cn\".*?\\}",
            "\\{[\\s\\S]*?\\}",
            "\\{\"[^\"]*\":\\s*\\[[^\\]]*\\][^}]*\\}"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {
                try {
                    String jsonStr = matcher.group(0);
                    // 修复常见错误
                    jsonStr = jsonStr.replaceAll(",\\s*}", "}");
                    jsonStr = jsonStr.replaceAll(",\\s*]", "]");
                    return objectMapper.readValue(jsonStr, Map.class);
                } catch (Exception e) {
                    // 继续尝试下一个模式
                }
            }
        }

        return null;
    }
}
