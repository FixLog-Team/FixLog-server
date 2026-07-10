package com.fixlog.presentation.controller.folder;

import com.fixlog.application.service.FolderService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.presentation.dto.request.FolderMoveRequest;
import com.fixlog.presentation.dto.request.FolderReorderRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import com.fixlog.presentation.dto.response.FolderDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping
    public Response createFolder(@RequestBody FolderRequest request) {
        FolderEntity folder = folderService.createFolder(request);
        return DataResponse.success(FolderDto.from(folder));
    }

    @GetMapping
    public Response getRootContents() {
        FolderContentsDto contents = folderService.getRootContents();
        return DataResponse.success(contents);
    }

    @GetMapping("/tree")
    public Response getFolderTree() {
        return DataResponse.success(folderService.getFolderTree());
    }

    @GetMapping("/{folderId}")
    public Response getFolder(@PathVariable String folderId) {
        FolderEntity folder = folderService.getFolder(folderId);
        return DataResponse.success(FolderDto.from(folder));
    }

    @GetMapping("/{folderId}/contents")
    public Response getFolderContents(@PathVariable String folderId) {
        FolderContentsDto contents = folderService.getFolderContents(folderId);
        return DataResponse.success(contents);
    }

    @PutMapping("/{folderId}")
    public Response updateFolder(@PathVariable String folderId,
                                 @RequestBody FolderRequest request) {
        FolderEntity folder = folderService.updateFolder(folderId, request);
        return DataResponse.success(FolderDto.from(folder));
    }

    @PatchMapping("/reorder")
    public Response reorderFolders(@RequestBody FolderReorderRequest request) {
        List<FolderDto> folders = folderService.reorderFolders(request.parentId(), request.folderIds())
                .stream().map(FolderDto::from).toList();
        return DataResponse.success(folders);
    }

    @PatchMapping("/{folderId}/move")
    public Response moveFolder(@PathVariable String folderId,
                               @RequestBody FolderMoveRequest request) {
        FolderEntity folder = folderService.moveFolder(folderId, request.parentId());
        return DataResponse.success(FolderDto.from(folder));
    }

    @DeleteMapping("/{folderId}")
    public Response deleteFolder(@PathVariable String folderId) {
        folderService.deleteFolder(folderId);
        return Response.success("폴더가 삭제되었습니다. 하위 폴더와 문서도 함께 삭제됩니다.");
    }
}
