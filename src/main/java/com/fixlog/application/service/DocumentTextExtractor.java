package com.fixlog.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DocumentTextExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Set<String> ALLOWED_TYPES =
            Set.of("paragraph", "header", "list", "code", "image", "table");
    private static final int MAX_BLOCKS = 5_000;
    private static final int MAX_TEXT_LEN = 100_000;
    private static final int MAX_ITEMS = 1_000;

    public void validateBlocks(JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            throw new BusinessException(Code.INVALID_REQUEST, "blocks는 배열이어야 합니다.");
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new BusinessException(Code.INVALID_REQUEST, "blocks 개수가 너무 많습니다.");
        }
        for (JsonNode b : blocks) {
            String type = b.path("type").asText("");
            if (!ALLOWED_TYPES.contains(type)) {
                throw new BusinessException(Code.INVALID_REQUEST, "허용되지 않는 block type: " + type);
            }
            JsonNode data = b.path("data");
            if (data.path("text").asText("").length() > MAX_TEXT_LEN
                    || data.path("code").asText("").length() > MAX_TEXT_LEN) {
                throw new BusinessException(Code.INVALID_REQUEST, "block text/code가 너무 깁니다.");
            }
            JsonNode items = data.path("items");
            if (items.isArray() && items.size() > MAX_ITEMS) {
                throw new BusinessException(Code.INVALID_REQUEST, "list items가 너무 많습니다.");
            }
        }
    }

    public String extract(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(blocksJson);
            JsonNode blocks = root.isArray() ? root : root.path("blocks");
            StringBuilder sb = new StringBuilder();
            for (JsonNode b : blocks) {
                String type = b.path("type").asText("");
                JsonNode data = b.path("data");
                switch (type) {
                    case "paragraph", "header" -> appendLine(sb, data.path("text").asText(""));
                    case "list" -> {
                        JsonNode items = data.path("items");
                        if (items.isArray()) {
                            List<String> parts = new ArrayList<>();
                            items.forEach(it -> parts.add(it.asText("")));
                            appendLine(sb, String.join(" ", parts));
                        }
                    }
                    case "code" -> appendLine(sb, data.path("code").asText(""));
                    default -> { /* image, table, unknown → skip */ }
                }
            }
            return stripHtml(sb.toString()).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void appendLine(StringBuilder sb, String s) {
        if (s == null || s.isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(s);
    }

    private String stripHtml(String s) {
        return HTML_TAG.matcher(s).replaceAll("");
    }
}
