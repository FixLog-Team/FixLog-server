package com.fixlog.presentation.controller.document;

import com.fixlog.application.service.DocumentService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.DocumentReorderRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.DocumentTitleRequest;
import com.fixlog.presentation.dto.response.DocumentDuplicateDto;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.DocumentPageDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public Response create(@RequestBody DocumentCreateRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.create(req)));
    }

    @GetMapping
    public Response list(@RequestParam(required = false) String folderId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return DataResponse.success(DocumentPageDto.from(documentService.list(folderId, pageable)));
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

    @PatchMapping("/reorder")
    public Response reorder(@RequestBody DocumentReorderRequest req) {
        List<DocumentDto> documents = documentService.reorder(req.folderId(), req.documentIds())
                .stream().map(DocumentDto::from).toList();
        return DataResponse.success(documents);
    }

    @PatchMapping("/{documentId}/move")
    public Response move(@PathVariable String documentId,
                         @RequestBody DocumentMoveRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.move(documentId, req)));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String documentId) {
        DocumentService.PdfResult result = documentService.downloadPdfResult(documentId);
        String filename = URLEncoder.encode(result.title() + ".pdf", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(result.bytes());
    }
}
