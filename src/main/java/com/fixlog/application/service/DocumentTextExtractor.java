package com.fixlog.application.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DocumentTextExtractor {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
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
                    // 이미지 본문은 비전 분석이 따로 다루지만, 사용자가 쓴 캡션은 그 자체로 검색 단서다
                    case "image" -> appendLine(sb, imageCaptionOf(data));
                    case "table" -> appendLine(sb, tableTextOf(data));
                    default -> { /* unknown → skip */ }
                }
            }
            return stripHtml(sb.toString()).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** 캡션 위치가 에디터마다 달라(BlockNote {@code caption}, Editor.js {@code caption}/{@code alt}) 둘 다 본다. */
    private String imageCaptionOf(JsonNode data) {
        String caption = data.path("caption").asText("");
        if (caption.isEmpty()) {
            caption = data.path("alt").asText("");
        }
        return caption;
    }

    /**
     * 표를 행 단위로 펼친다. 셀 구분은 공백이면 열 경계가 뭉개지고, 줄바꿈이면 행이 쪼개져
     * 청킹에서 서로 다른 행이 섞인다. 그래서 셀은 공백, 행은 줄바꿈으로 나눈다.
     */
    private String tableTextOf(JsonNode data) {
        JsonNode rows = data.path("content");
        if (!rows.isArray()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isArray()) continue;
            List<String> cells = new ArrayList<>();
            row.forEach(cell -> {
                String text = cell.asText("").trim();
                if (!text.isEmpty()) cells.add(text);
            });
            if (!cells.isEmpty()) lines.add(String.join(" ", cells));
        }
        return String.join("\n", lines);
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
