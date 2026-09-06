package com.sw1.umltool.features.importexport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiUmlDetectionResponse(boolean success, DiagramDetection diagram, List<String> warnings,
                                     Double confidence, String summary) {
    public record DiagramDetection(List<ClassDetection> classes, List<RelationDetection> relations,
                                   List<AssociationClassDetection> associationClasses) {}
    public record ClassDetection(String ref, String name, @JsonProperty("abstract") Boolean abstractClass,
                                 List<AttributeDetection> attributes, List<MethodDetection> methods) {}
    public record AttributeDetection(String name, String type, String visibility,
                                     @JsonProperty("static") Boolean staticAttribute,
                                     @JsonProperty("final") Boolean finalAttribute, String defaultValue,
                                     Boolean primaryKey) {}
    public record MethodDetection(String name, String returnType, String visibility,
                                  @JsonProperty("static") Boolean staticMethod,
                                  List<ParameterDetection> parameters) {}
    public record ParameterDetection(String name, String type) {}
    public record RelationDetection(String ref, String type, String sourceClassName, String targetClassName,
                                    String sourceClassRef, String targetClassRef,
                                    String sourceMultiplicity, String targetMultiplicity,
                                    String sourceRole, String targetRole,
                                    Boolean sourceNavigable, Boolean targetNavigable) {}
    public record AssociationClassDetection(String relationRef, String className, String classRef) {}
}
