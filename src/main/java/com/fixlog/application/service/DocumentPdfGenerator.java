package com.fixlog.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class DocumentPdfGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generate(DocumentEntity doc) {
        return generate(doc, null);
    }

    /**
     * 워터마크를 각인해 내보낸다 (FR-SEC-003).
     *
     * <p>반출 자체를 막지는 못한다. 유출됐을 때 누구를 통해 나갔는지 남기는 것이 목적이다.
     */
    public byte[] generate(DocumentEntity doc, String watermark) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);

            PdfDocumentInfo info = pdf.getDocumentInfo();
            info.setTitle(doc.getTitle() == null ? "" : doc.getTitle());

            try (Document layout = new Document(pdf)) {
                try {
                    PdfFont font = PdfFontFactory.createFont(
                            "fonts/NanumGothic.ttf",
                            PdfEncodings.IDENTITY_H,
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    layout.setFont(font);
                } catch (Exception fontEx) {
                    // 폰트 미존재 시 기본 폰트 폴백 (운영 환경에서는 NanumGothic.ttf 필수)
                }

                if (watermark != null && !watermark.isBlank()) {
                    layout.add(new Paragraph(watermark)
                            .setFontSize(9)
                            .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(150, 150, 150)));
                }

                layout.add(new Paragraph(doc.getTitle()).setBold().setFontSize(20));

                JsonNode blocks = parseBlocks(doc.getBlocks());
                if (blocks != null) {
                    for (JsonNode b : blocks) {
                        renderBlock(layout, b);
                    }
                }
            }
            return baos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(Code.UNKNOWN, "PDF 생성에 실패했습니다.");
        }
    }

    private JsonNode parseBlocks(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(blocksJson);
            return root.isArray() ? root : root.path("blocks");
        } catch (Exception e) {
            return null;
        }
    }

    private void renderBlock(Document layout, JsonNode block) {
        String type = block.path("type").asText("");
        JsonNode data = block.path("data");

        switch (type) {
            case "header" -> {
                int level = data.path("level").asInt(1);
                float fontSize = switch (level) {
                    case 1 -> 20f;
                    case 2 -> 16f;
                    default -> 14f;
                };
                String text = data.path("text").asText("");
                layout.add(new Paragraph(stripHtml(text)).setBold().setFontSize(fontSize));
            }
            case "paragraph" -> {
                String text = data.path("text").asText("");
                layout.add(new Paragraph(stripHtml(text)).setFontSize(12));
            }
            case "list" -> {
                JsonNode items = data.path("items");
                if (items.isArray()) {
                    List pdfList = new List();
                    items.forEach(item -> pdfList.add(new ListItem(stripHtml(item.asText("")))));
                    layout.add(pdfList);
                }
            }
            case "code" -> {
                String code = data.path("code").asText("");
                layout.add(new Paragraph(code)
                        .setFontSize(11)
                        .setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(240, 240, 240)));
            }
            default -> { /* image, table → skip */ }
        }
    }

    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "");
    }
}
