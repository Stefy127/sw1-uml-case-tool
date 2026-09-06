package com.sw1.umltool.features.importexport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class XmiImportApplyRequest {
    @NotBlank
    private String diagramId;
    private long baseVersion;
    private MultipartFile file;
}
