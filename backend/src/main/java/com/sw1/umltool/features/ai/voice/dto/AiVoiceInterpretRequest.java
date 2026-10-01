package com.sw1.umltool.features.ai.voice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiVoiceInterpretRequest(
        @NotBlank @Size(max = 2000) String text,
        String language,
        @Valid DiagramContext diagramContext
) {
    public record DiagramContext(List<ClassContext> classes,
                                 List<RelationContext> relations,
                                 List<AssociationClassLinkContext> associationClassLinks) {
        public DiagramContext(List<ClassContext> classes) {
            this(classes, null, null);
        }
    }
    public record ClassContext(String id, String name, List<AttributeContext> attributes, List<MethodContext> methods) {
        public ClassContext(String id, String name) {
            this(id, name, null, null);
        }
    }
    public record AttributeContext(String id, String name, String type) {}
    public record MethodContext(String id, String name, String returnType, List<ParameterContext> parameters) {}
    public record ParameterContext(String id, String name, String type) {}
    public record RelationContext(String id, String sourceClassId, String targetClassId, String type,
                                  MultiplicityContext sourceMultiplicity, MultiplicityContext targetMultiplicity) {}
    public record MultiplicityContext(String lower, String upper) {}
    public record AssociationClassLinkContext(String id, String relationId, String classId) {}
}
