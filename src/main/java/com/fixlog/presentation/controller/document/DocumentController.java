package com.fixlog.presentation.controller.document;

import com.fixlog.application.service.DocumentHistoryService;
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
import com.fixlog.presentation.dto.response.DocumentHistoryDetailDto;
import com.fixlog.presentation.dto.response.DocumentHistoryPageDto;
import com.fixlog.presentation.dto.response.DocumentPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Document")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentHistoryService documentHistoryService;

    public DocumentController(DocumentService documentService,
                              DocumentHistoryService documentHistoryService) {
        this.documentService = documentService;
        this.documentHistoryService = documentHistoryService;
    }

    @GetMapping("/{documentId}/history")
    @Operation(operationId = "listDocumentHistory")
    public Response listHistory(@PathVariable String documentId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return DataResponse.success(
                DocumentHistoryPageDto.from(documentHistoryService.list(documentId, pageable)));
    }

    @GetMapping("/{documentId}/history/{historyId}")
    @Operation(operationId = "getDocumentHistory")
    public Response getHistory(@PathVariable String documentId,
                               @PathVariable String historyId) {
        return DataResponse.success(
                DocumentHistoryDetailDto.from(documentHistoryService.getVersion(documentId, historyId)));
    }

    @PostMapping("/{documentId}/history/{historyId}/restore")
    @Operation(operationId = "restoreDocumentHistory")
    public Response restoreHistory(@PathVariable String documentId,
                                   @PathVariable String historyId) {
        return DataResponse.success(
                DocumentDto.from(documentService.restoreFromHistory(documentId, historyId)));
    }

    @PostMapping
    @Operation(operationId = "createDocument")
    public Response create(@RequestBody DocumentCreateRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.create(req)));
    }

    @GetMapping
    @Operation(operationId = "listDocuments")
    public Response list(@RequestParam(required = false) String folderId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return DataResponse.success(DocumentPageDto.from(documentService.list(folderId, pageable)));
    }

    @GetMapping("/{documentId}")
    @Operation(operationId = "getDocument")
    public Response getDocument(@PathVariable String documentId) {
        return DataResponse.success(DocumentDto.from(documentService.getDocument(documentId)));
    }

    @PutMapping("/{documentId}")
    @Operation(operationId = "saveDocument")
    public Response saveContent(@PathVariable String documentId,
                                @Valid @RequestBody DocumentSaveRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.saveContent(documentId, req)));
    }

    @PatchMapping("/{documentId}/title")
    @Operation(operationId = "updateDocumentTitle")
    public Response updateTitle(@PathVariable String documentId,
                                @Valid @RequestBody DocumentTitleRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.updateTitle(documentId, req)));
    }

    @GetMapping("/{documentId}/save-state")
    @Operation(operationId = "getDocumentSaveState")
    public Response getSaveState(@PathVariable String documentId) {
        return DataResponse.success(documentService.getSaveState(documentId));
    }

    @PostMapping("/{documentId}/duplicate")
    @Operation(operationId = "duplicateDocument")
    public Response duplicate(@PathVariable String documentId) {
        return DataResponse.success(DocumentDuplicateDto.from(documentService.duplicate(documentId)));
    }

    @DeleteMapping("/{documentId}")
    @Operation(operationId = "deleteDocument")
    public Response delete(@PathVariable String documentId) {
        documentService.delete(documentId);
        return Response.success("문서가 삭제되었습니다.");
    }

    @PatchMapping("/reorder")
    @Operation(operationId = "reorderDocuments")
    public Response reorder(@RequestBody DocumentReorderRequest req) {
        List<DocumentDto> documents = documentService.reorder(req.folderId(), req.documentIds())
                .stream().map(DocumentDto::from).toList();
        return DataResponse.success(documents);
    }

    @PatchMapping("/{documentId}/move")
    @Operation(operationId = "moveDocument")
    public Response move(@PathVariable String documentId,
                         @RequestBody DocumentMoveRequest req) {
        return DataResponse.success(DocumentDto.from(documentService.move(documentId, req)));
    }

    @GetMapping("/{documentId}/download")
    @Operation(operationId = "downloadDocument")
    public ResponseEntity<byte[]> download(@PathVariable String documentId) {
        DocumentService.PdfResult result = documentService.downloadPdfResult(documentId);
        String filename = URLEncoder.encode(result.title() + ".pdf", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(result.bytes());
    }
}
