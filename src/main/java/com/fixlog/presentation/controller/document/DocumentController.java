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
import com.fixlog.presentation.dto.response.DocumentRevisionDto;
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
    private final DocumentHistoryService documentHistoryService;

    public DocumentController(DocumentService documentService,
                              DocumentHistoryService documentHistoryService) {
        this.documentService = documentService;
        this.documentHistoryService = documentHistoryService;
    }

    @GetMapping("/{documentId}/history")
    public Response listHistory(@PathVariable String documentId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return DataResponse.success(
                DocumentHistoryPageDto.from(documentHistoryService.list(documentId, pageable)));
    }

    @GetMapping("/{documentId}/history/{historyId}")
    public Response getHistory(@PathVariable String documentId,
                               @PathVariable String historyId) {
        return DataResponse.success(
                DocumentHistoryDetailDto.from(documentHistoryService.getVersion(documentId, historyId)));
    }

    @PostMapping("/{documentId}/history/{historyId}/restore")
    public Response restoreHistory(@PathVariable String documentId,
                                   @PathVariable String historyId) {
        return DataResponse.success(
                DocumentDto.from(documentService.restoreFromHistory(documentId, historyId)));
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

    /** 리비전 목록. 본문 없이 언제 누가 저장했는지만 내려준다 (FR-REV-004). */
    @GetMapping("/{documentId}/revisions")
    public Response revisions(@PathVariable String documentId) {
        return DataResponse.success(documentService.revisions(documentId).stream()
                .map(DocumentRevisionDto::from).toList());
    }

    @GetMapping("/{documentId}/revisions/{revisionNo}")
    public Response revision(@PathVariable String documentId, @PathVariable int revisionNo) {
        return DataResponse.success(documentService.revision(documentId, revisionNo));
    }

    /** 해당 시점으로 되돌린다. 되돌린 결과도 새 리비전으로 남는다 (FR-REV-006). */
    @PostMapping("/{documentId}/revisions/{revisionNo}/restore")
    public Response restore(@PathVariable String documentId, @PathVariable int revisionNo) {
        return DataResponse.success(DocumentDto.from(documentService.restore(documentId, revisionNo)));
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
