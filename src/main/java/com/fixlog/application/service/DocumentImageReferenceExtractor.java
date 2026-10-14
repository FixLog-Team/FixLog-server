package com.fixlog.application.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서 blocks JSON이 참조하는 이미지 id를 뽑는다.
 *
 * <p>이미지는 워크스페이스 단위로 업로드되고 문서와의 연결은 블록 안의 URL로만 드러난다.
 * 블록 스키마가 에디터마다 달라(BlockNote {@code props.url}, Editor.js {@code data.file.url}) 경로를
 * 일일이 따라가는 대신, 업로드가 돌려주는 조회 URL 형태를 문서 전체에서 찾는다.
 */
@Component
public class DocumentImageReferenceExtractor {

    /** 업로드 응답이 주는 조회 URL: .../api/images/{uuid} */
    private static final Pattern IMAGE_URL = Pattern.compile(
            "/api/images/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    /** 등장 순서를 유지한 채 중복을 없앤 이미지 id 목록. 같은 이미지를 여러 번 넣어도 한 번만 다룬다. */
    public List<String> extract(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return List.of();
        }

        // 블록 스키마를 신뢰할 수 없으므로 유효한 JSON인지만 확인하고 본문 전체에서 URL을 찾는다
        try {
            JsonNode root = objectMapper.readTree(blocksJson);
            if (root == null || root.isNull()) {
                return List.of();
            }
        } catch (Exception e) {
            return List.of();
        }

        LinkedHashSet<String> imageIds = new LinkedHashSet<>();
        Matcher matcher = IMAGE_URL.matcher(blocksJson);
        while (matcher.find()) {
            imageIds.add(matcher.group(1));
        }
        return List.copyOf(imageIds);
    }
}
