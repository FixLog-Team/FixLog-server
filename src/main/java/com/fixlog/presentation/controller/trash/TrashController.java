package com.fixlog.presentation.controller.trash;

import com.fixlog.application.service.LabelService;
import com.fixlog.application.service.TrashService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.LabelEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.request.LabelRequest;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.LabelDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TrashController {

    private final TrashService trashService;
    private final LabelService labelService;

    public TrashController(TrashService trashService, LabelService labelService) {
        this.trashService = trashService;
        this.labelService = labelService;
    }

    // ---------- 휴지통 ----------

    @GetMapping("/trash")
    public Response trash() {
        return DataResponse.success(trashService.list());
    }

    @PostMapping("/trash/{resourceType}/{resourceId}/restore")
    public Response restore(@PathVariable ResourceType resourceType, @PathVariable String resourceId) {
        trashService.restore(resourceType, resourceId);
        return Response.success("복원했습니다.");
    }

    @DeleteMapping("/trash/{resourceType}/{resourceId}")
    public Response purge(@PathVariable ResourceType resourceType, @PathVariable String resourceId) {
        trashService.purge(resourceType, resourceId);
        return Response.success("영구 삭제했습니다.");
    }

    // ---------- 라벨 ----------

    @GetMapping("/labels")
    public Response labels() {
        return DataResponse.success(labelService.labelsOfWorkspace().stream().map(LabelDto::from).toList());
    }

    @GetMapping("/labels/{labelId}/documents")
    public Response documentsWithLabel(@PathVariable UUID labelId) {
        return DataResponse.success(
                labelService.documentsWith(labelId).stream().map(DocumentDto::from).toList());
    }

    @GetMapping("/documents/{documentId}/labels")
    public Response documentLabels(@PathVariable String documentId) {
        return DataResponse.success(labelService.labelsOf(documentId).stream().map(LabelDto::from).toList());
    }

    @PostMapping("/documents/{documentId}/labels")
    public Response attachLabel(@PathVariable String documentId, @RequestBody LabelRequest request) {
        LabelEntity label = labelService.attach(documentId, request.labelName());
        return DataResponse.success(LabelDto.from(label));
    }

    @DeleteMapping("/documents/{documentId}/labels/{labelId}")
    public Response detachLabel(@PathVariable String documentId, @PathVariable UUID labelId) {
        labelService.detach(documentId, labelId);
        return Response.success("라벨을 제거했습니다.");
    }
}
