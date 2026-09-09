package com.sw1.umltool.features.project.dto;

import com.sw1.umltool.features.project.model.ProjectShareMode;

public record ShareLinkResponse(ProjectShareMode mode, String url) {}
