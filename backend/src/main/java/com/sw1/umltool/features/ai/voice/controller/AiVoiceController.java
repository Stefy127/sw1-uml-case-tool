package com.sw1.umltool.features.ai.voice.controller;

import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretRequest;
import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretResponse;
import com.sw1.umltool.features.ai.voice.service.AiVoiceInterpretationService;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/ai/voice")
public class AiVoiceController {
    private final AiVoiceInterpretationService service;
    private final ProjectAccessService access;
    private final DiagramRepository diagrams;
    private final DiagramStateSerializer serializer;

    public AiVoiceController(AiVoiceInterpretationService service) {
        this(service, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiVoiceController(AiVoiceInterpretationService service, ProjectAccessService access,
                             DiagramRepository diagrams, DiagramStateSerializer serializer) {
        this.service = service;
        this.access = access;
        this.diagrams = diagrams;
        this.serializer = serializer;
    }

    @PostMapping("/interpret")
    public ResponseEntity<AiVoiceInterpretResponse> interpret(@Valid @RequestBody AiVoiceInterpretRequest request,
                                                               Authentication authentication) {
        AiVoiceInterpretRequest trusted = request;
        if (request.diagramContext() != null && access != null && diagrams != null
                && (request.diagramId() == null || request.projectId() == null)) {
            throw new IllegalArgumentException("El contexto de voz debe identificar un proyecto y un diagrama autorizados.");
        }
        if (request.diagramContext() != null && request.diagramId() != null && access != null && diagrams != null) {
            DiagramEntity diagram = diagrams.findById(request.diagramId())
                    .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado."));
            if (request.projectId() == null || !request.projectId().equals(diagram.getProjectId())) {
                throw new IllegalArgumentException("El diagrama no pertenece al proyecto indicado.");
            }
            if (authentication == null) throw new IllegalStateException("Autenticación requerida.");
            access.requireDiagramMember(request.diagramId(), authentication.getName());
            trusted = new AiVoiceInterpretRequest(request.text(), request.language(), trustedContext(diagram),
                    request.projectId(), request.diagramId());
        }
        return ResponseEntity.ok(service.interpret(trusted));
    }

    private AiVoiceInterpretRequest.DiagramContext trustedContext(DiagramEntity entity) {
        var model = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        List<AiVoiceInterpretRequest.ClassContext> classes = model.getClasses().stream().map(this::classContext).toList();
        List<AiVoiceInterpretRequest.RelationContext> relations = model.getRelations().stream().map(this::relationContext).toList();
        List<AiVoiceInterpretRequest.AssociationClassLinkContext> links = model.getAssociationClassLinks().stream()
                .map(link -> new AiVoiceInterpretRequest.AssociationClassLinkContext(link.getId(), link.getRelationId(), link.getClassId())).toList();
        return new AiVoiceInterpretRequest.DiagramContext(classes, relations, links);
    }

    private AiVoiceInterpretRequest.ClassContext classContext(UmlClass value) {
        List<AiVoiceInterpretRequest.AttributeContext> attributes = value.getAttributes().stream()
                .map(item -> new AiVoiceInterpretRequest.AttributeContext(item.getId(), item.getName(), item.getType())).toList();
        List<AiVoiceInterpretRequest.MethodContext> methods = value.getMethods().stream().map(this::methodContext).toList();
        return new AiVoiceInterpretRequest.ClassContext(value.getId(), value.getName(), attributes, methods);
    }

    private AiVoiceInterpretRequest.MethodContext methodContext(UmlMethod value) {
        List<AiVoiceInterpretRequest.ParameterContext> parameters = value.getParameters().stream()
                .map(item -> new AiVoiceInterpretRequest.ParameterContext(item.getId(), item.getName(), item.getType())).toList();
        return new AiVoiceInterpretRequest.MethodContext(value.getId(), value.getName(), value.getReturnType(), parameters);
    }

    private AiVoiceInterpretRequest.RelationContext relationContext(UmlRelation value) {
        var source = value.getSourceMultiplicity();
        var target = value.getTargetMultiplicity();
        return new AiVoiceInterpretRequest.RelationContext(value.getId(), value.getSourceClassId(), value.getTargetClassId(),
                value.getType() == null ? null : value.getType().name(),
                source == null ? null : new AiVoiceInterpretRequest.MultiplicityContext(source.getLower(), source.getUpper()),
                target == null ? null : new AiVoiceInterpretRequest.MultiplicityContext(target.getLower(), target.getUpper()));
    }
}
