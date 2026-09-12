package com.sw1.umltool.features.generator.dto;

public record GenerateBackendRequest(
        String basePackage,
        String artifactId,
        String groupId,
        String projectName
) {
    public String effectiveBasePackage() { return blank(basePackage) ? "com.generated.app" : basePackage.trim(); }
    public String effectiveArtifactId(String fallback) { return blank(artifactId) ? fallback : artifactId.trim(); }
    public String effectiveGroupId() { return blank(groupId) ? "com.generated" : groupId.trim(); }
    public String effectiveProjectName(String fallback) { return blank(projectName) ? fallback : projectName.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
