package com.sw1.umltool.features.importexport.controller;

import com.sw1.umltool.features.importexport.dto.XmiImportPreviewResponse;
import com.sw1.umltool.features.importexport.service.XmiImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/import/xmi")
public class XmiImportController {
    private final XmiImportService service;

    public XmiImportController(XmiImportService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<XmiImportPreviewResponse> preview(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.preview(file));
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<XmiImportPreviewResponse> apply(@RequestParam String diagramId,
                                                            @RequestParam long baseVersion,
                                                            @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.ok(service.apply(diagramId, baseVersion, file, authentication == null ? null : authentication.getName()));
    }
}
