package com.fixlog.presentation.controller.bulk;

import com.fixlog.application.service.BulkService;
import com.fixlog.application.service.DocumentService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.BulkDeleteRequest;
import com.fixlog.presentation.dto.request.BulkMoveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/bulk")
@Tag(name = "Bulk")
public class BulkController {

    private final BulkService bulkService;

    public BulkController(BulkService bulkService) {
        this.bulkService = bulkService;
    }

    @DeleteMapping
    @Operation(operationId = "bulkDeleteMixed")
    public Response bulkDelete(@Valid @RequestBody BulkDeleteRequest req) {
        return DataResponse.success(bulkService.bulkDelete(req.documentIds(), req.folderIds()));
    }

    @PatchMapping("/move")
    @Operation(operationId = "bulkMoveMixed")
    public Response bulkMove(@Valid @RequestBody BulkMoveRequest req) {
        return DataResponse.success(bulkService.bulkMove(req.documentIds(), req.folderIds(), req.targetFolderId()));
    }

    @GetMapping("/download")
    @Operation(operationId = "bulkDownloadMixed")
    public ResponseEntity<byte[]> bulkDownload(
            @RequestParam(required = false)
            @Size(max = 100, message = "문서는 한 번에 최대 100개까지 다운로드할 수 있습니다.")
            List<String> documentIds,

            @RequestParam(required = false)
            @Size(max = 50, message = "폴더는 한 번에 최대 50개까지 다운로드할 수 있습니다.")
            List<String> folderIds) {
        DocumentService.BulkDownloadResult result = bulkService.bulkDownload(documentIds, folderIds);
        String filename = URLEncoder.encode("fixlog-bulk.zip", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(result.zipBytes());
    }
}
