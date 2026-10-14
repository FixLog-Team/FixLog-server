package com.fixlog;

import com.fixlog.application.service.ImageStorageService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.domain.model.ImageEntity;
import com.fixlog.presentation.controller.image.ImageController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이미지 조회는 서명 URL 리다이렉트가 아니라 서버가 바이트를 직접 내려준다.
 * 권한을 매 요청 확인할 수 있고, 스토리지가 다른 origin 이라 생기는 CORS 의존도 없어진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageControllerTest {

    @Mock ImageStorageService imageStorageService;
    @Mock WorkspaceContext workspaceContext;

    private ImageController controller;
    private ImageEntity image;

    @BeforeEach
    void setUp() {
        controller = new ImageController(imageStorageService, workspaceContext);
        image = new ImageEntity(
                "img-1", UUID.randomUUID(), "images/ws/img.png",
                "image/png", "img.png", 3L, "abc123", "user-1");
        given(imageStorageService.requireReadable("img-1")).willReturn(image);
        given(imageStorageService.download(image)).willReturn(new byte[]{1, 2, 3});
    }

    @Test
    void 이미지를_서버가_직접_내려준다() {
        ResponseEntity<byte[]> response = controller.getImage("img-1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        // 리다이렉트가 아니어야 한다 — 서명 URL 이 나가면 권한 회수가 불가능해진다
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    void 캐시가_유효하면_스토리지를_읽지_않고_304로_끝낸다() {
        ResponseEntity<byte[]> response = controller.getImage("img-1", "\"abc123\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
        verify(imageStorageService, never()).download(any());
    }

    @Test
    void 캐시가_유효해도_권한은_매번_확인한다() {
        controller.getImage("img-1", "\"abc123\"");

        verify(imageStorageService).requireReadable("img-1");
    }

    @Test
    void 내용_해시를_ETag로_내려_재검증이_304로_끝나게_한다() {
        ResponseEntity<byte[]> response = controller.getImage("img-1", null);

        assertThat(response.getHeaders().getETag()).isEqualTo("\"abc123\"");
        assertThat(response.getHeaders().getCacheControl()).contains("private");
    }

    @Test
    void 해시가_없는_예전_이미지는_ETag_없이_내려간다() {
        ImageEntity legacy = new ImageEntity(
                "img-2", UUID.randomUUID(), "images/ws/old.png",
                "image/png", "old.png", 3L, null, "user-1");
        given(imageStorageService.requireReadable("img-2")).willReturn(legacy);
        given(imageStorageService.download(legacy)).willReturn(new byte[]{9});

        ResponseEntity<byte[]> response = controller.getImage("img-2", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isNull();
    }

    @Test
    void 알_수_없는_형식이면_octet_stream으로_내린다() {
        ImageEntity broken = new ImageEntity(
                "img-3", UUID.randomUUID(), "images/ws/x", "", "x", 1L, "hash3", "user-1");
        given(imageStorageService.requireReadable("img-3")).willReturn(broken);
        given(imageStorageService.download(broken)).willReturn(new byte[]{7});

        ResponseEntity<byte[]> response = controller.getImage("img-3", null);

        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void 다른_ETag면_본문을_다시_내려준다() {
        ResponseEntity<byte[]> response = controller.getImage("img-1", "\"stale\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("\"abc123\"");
    }
}
