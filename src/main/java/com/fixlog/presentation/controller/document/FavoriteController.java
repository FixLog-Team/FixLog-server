package com.fixlog.presentation.controller.document;

import com.fixlog.application.service.FavoriteService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.response.FavoriteDocumentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Document")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites")
    @Operation(operationId = "listFavorites", summary = "즐겨찾기 문서 목록")
    public Response list() {
        List<FavoriteDocumentDto> result = favoriteService.list().stream()
                .map(FavoriteDocumentDto::from)
                .toList();
        return DataResponse.success(result);
    }

    @PostMapping("/{documentId}/favorite")
    @Operation(operationId = "addFavorite", summary = "즐겨찾기 추가")
    public Response add(@PathVariable String documentId) {
        favoriteService.add(documentId);
        return Response.success("즐겨찾기에 추가되었습니다.");
    }

    @DeleteMapping("/{documentId}/favorite")
    @Operation(operationId = "removeFavorite", summary = "즐겨찾기 제거")
    public Response remove(@PathVariable String documentId) {
        favoriteService.remove(documentId);
        return Response.success("즐겨찾기에서 제거되었습니다.");
    }
}
