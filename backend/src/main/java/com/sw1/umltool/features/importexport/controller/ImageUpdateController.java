package com.sw1.umltool.features.importexport.controller;

import com.sw1.umltool.features.importexport.dto.*;
import com.sw1.umltool.features.importexport.service.ImageUpdateService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import/image")
public class ImageUpdateController {
    private final ImageUpdateService service;

    public ImageUpdateController(ImageUpdateService service) { this.service = service; }

    @PostMapping(value = "/update-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUpdatePreviewResponse> preview(
            @RequestParam String diagramId,
            @RequestParam(defaultValue = "-1") long baseVersion,
            @RequestParam(defaultValue = "") String instruction,
            @RequestParam(defaultValue = "false") boolean allowDestructiveChanges,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(service.preview(diagramId, baseVersion, file, instruction,
                allowDestructiveChanges, authentication.getName()));
    }

    @PostMapping("/update-apply")
    public ResponseEntity<ImageUpdateService.DiagramUpdateResult> apply(
            @Valid @RequestBody ImageUpdateApplyRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.apply(request, authentication.getName()));
    }
}
