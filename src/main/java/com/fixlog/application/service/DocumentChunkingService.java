package com.fixlog.application.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentChunkingService {

    private static final int MAX_CHUNK_SIZE = 500;

    /**
     * plainText(블록 추출 텍스트)를 줄 단위로 청킹한다.
     * 각 줄은 paragraph/header/list/code 블록에 해당하며,
     * MAX_CHUNK_SIZE를 초과하기 전까지 하나의 청크로 묶는다.
     */
    public List<String> chunk(String plainText) {
        if (plainText == null || plainText.isBlank()) return List.of();

        String[] lines = plainText.split("\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (current.length() + line.length() + 1 > MAX_CHUNK_SIZE && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }

            if (!current.isEmpty()) current.append('\n');
            current.append(line);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return chunks.isEmpty() ? List.of(plainText.trim()) : chunks;
    }
}
