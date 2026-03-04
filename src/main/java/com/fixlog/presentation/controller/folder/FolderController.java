package com.fixlog.presentation.controller.folder;

import com.fixlog.application.service.FolderService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
public class FolderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FolderController.class);

    private FolderService folderService;

    @Autowired
    public void setFolderService(FolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping
    public Response createFolder(@RequestBody FolderRequest request) {
        FolderEntity folder = folderService.createFolder(request);
        return DataResponse.success(FolderDto.from(folder));
    }

    @GetMapping("/workspace/{workspaceId}")
    public Response getFoldersByWorkspace(@PathVariable String workspaceId) {
        List<FolderEntity> folders = folderService.getFoldersByWorkspace(workspaceId);
        List<FolderDto> result = folders.stream().map(FolderDto::from).toList();
        return DataResponse.success(result);
    }

    @GetMapping("/folder/{folderId}/workspace/{workspaceId}")
    public Response getFolder(@PathVariable String folderId, @PathVariable String workspaceId) {
        FolderEntity folder = folderService.getFolder(folderId, workspaceId);
        return DataResponse.success(FolderDto.from(folder));
    }

    @PutMapping("/{folderId}")
    public Response updateFolder(@PathVariable String folderId,
                                 @RequestBody FolderRequest request) {
        FolderEntity folder = folderService.updateFolder(folderId, request);
        return DataResponse.success(FolderDto.from(folder));
    }

    @DeleteMapping("/{folderId}/{workspaceId}")
    public Response deleteFolder(@PathVariable String folderId, @PathVariable String workspaceId) {
        folderService.deleteFolder(folderId, workspaceId);
        return Response.success("폴더가 삭제되었습니다.");
    }
}
