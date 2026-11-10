package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.dto.*;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.PhotoReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/readings/photos")
@RequiredArgsConstructor
public class PhotoReadingController {
    private final PhotoReadingService photos;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PhotoReadingDto> upload(@RequestParam String meterId, @RequestParam MultipartFile file,
                                              @RequestParam(defaultValue = "true") boolean recognize) {
        return ApiResponse.ok(photos.upload(meterId, file, CurrentUser.required(), recognize));
    }

    @GetMapping
    public ApiResponse<PageResult<PhotoReadingDto>> list(@RequestParam(required = false) String meterId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(photos.list(meterId, page, pageSize, CurrentUser.required()));
    }

    @GetMapping("/{id}")
    public ApiResponse<PhotoReadingDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(photos.detail(id, CurrentUser.required()));
    }

    @GetMapping(value = "/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(photos.image(id, CurrentUser.required()));
    }

    @PostMapping("/{id}/recognize")
    public ApiResponse<PhotoReadingDto> recognize(@PathVariable Long id, @Valid @RequestBody(required = false) PhotoRecognizeRequest request) {
        return ApiResponse.ok(photos.recognize(id, request, CurrentUser.required()));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<PhotoReadingDto> confirm(@PathVariable Long id, @Valid @RequestBody PhotoConfirmRequest request) {
        return ApiResponse.ok(photos.confirm(id, request, CurrentUser.required()));
    }
}
