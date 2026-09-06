package com.sw1.umltool.features.importexport.controller;

import com.sw1.umltool.features.importexport.dto.ImageImportPreviewResponse;
import com.sw1.umltool.features.importexport.service.ImageImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import/image")
public class ImageImportController {
    private final ImageImportService service;
    public ImageImportController(ImageImportService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageImportPreviewResponse> preview(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.preview(file));
    }
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageImportPreviewResponse> apply(@RequestParam String diagramId,
            @RequestParam long baseVersion, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.apply(diagramId, baseVersion, file));
    }
}
