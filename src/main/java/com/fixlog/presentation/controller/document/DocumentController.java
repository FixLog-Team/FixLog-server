package com.fixlog.presentation.controller.document;

import com.fixlog.application.service.DocumentService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.DocumentTitleRequest;
import com.fixlog.presentation.dto.response.DocumentDuplicateDto;
import com.fixlog.presentation.dto.response.DocumentDto;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentController.class);

    private DocumentService documentService;

    @Autowired
    public void setDocumentService(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{documentId}")
    public Response getDocument(@PathVariable String documentId) {
        return DataResponse.success(DocumentDto.from(documentService.getDocument(documentId)));
    }

    @PutMapping("/{documentId}")
    public Response saveContent(@PathVariable String documentId,
                                @Valid @RequestBody DocumentSaveRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.saveContent(documentId, req)));
    }

    @PatchMapping("/{documentId}/title")
    public Response updateTitle(@PathVariable String documentId,
                                @Valid @RequestBody DocumentTitleRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.updateTitle(documentId, req)));
    }

    @GetMapping("/{documentId}/save-state")
    public Response getSaveState(@PathVariable String documentId) {
        return DataResponse.success(documentService.getSaveState(documentId));
    }

    @PostMapping("/{documentId}/duplicate")
    public Response duplicate(@PathVariable String documentId) {
        return DataResponse.success(DocumentDuplicateDto.from(documentService.duplicate(documentId)));
    }

    @DeleteMapping("/{documentId}")
    public Response delete(@PathVariable String documentId) {
        documentService.delete(documentId);
        return Response.success("문서가 삭제되었습니다.");
    }

    @PatchMapping("/{documentId}/move")
    public Response move(@PathVariable String documentId,
                         @RequestBody DocumentMoveRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.move(documentId, req)));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<?> download(@PathVariable String documentId) {
        try {
            DocumentEntity doc = documentService.getDocument(documentId);
            byte[] pdf = documentService.downloadPdf(documentId);
            String filename = URLEncoder.encode(doc.getTitle() + ".pdf", StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .body(pdf);
        } catch (BusinessException e) {
            HttpStatus status = e.getCode() == Code.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Response.failure(e.getCode(), e.getMessage()));
        }
    }
}
