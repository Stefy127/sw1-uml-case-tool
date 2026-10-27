package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.diagram.model.canonical.*;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.*;
import com.sw1.umltool.features.diagram.operation.payload.*;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.operation.DiagramStateCloner;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.importexport.dto.*;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.*;

@Service
public class ImageUpdateService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("CREATE_CLASS", "DELETE_CLASS", "RENAME_CLASS",
            "ADD_ATTRIBUTE", "RENAME_ATTRIBUTE", "CHANGE_ATTRIBUTE_TYPE", "REMOVE_ATTRIBUTE",
            "ADD_METHOD", "RENAME_METHOD", "REMOVE_METHOD", "CREATE_RELATION", "REMOVE_RELATION",
            "CHANGE_RELATION_TYPE");
    private static final Set<String> DESTRUCTIVE = Set.of("DELETE_CLASS", "REMOVE_ATTRIBUTE", "REMOVE_METHOD", "REMOVE_RELATION");
    private final DiagramRepository diagrams;
    private final DiagramStateSerializer serializer;
    private final DiagramStateCloner cloner;
    private final DiagramOperationApplier applier;
    private final CanonicalModelValidator validator;
    private final ImageUpdateAiService ai;
    private final ProjectAccessService access;
    private final CollaborationBroadcastService broadcaster;

    public ImageUpdateService(DiagramRepository diagrams, DiagramStateSerializer serializer, DiagramStateCloner cloner,
                              DiagramOperationApplier applier, CanonicalModelValidator validator, ImageUpdateAiService ai,
                              ProjectAccessService access, CollaborationBroadcastService broadcaster) {
        this.diagrams = diagrams; this.serializer = serializer; this.cloner = cloner; this.applier = applier;
        this.validator = validator; this.ai = ai; this.access = access; this.broadcaster = broadcaster;
    }

    public ImageUpdatePreviewResponse preview(String diagramId, long baseVersion, MultipartFile file,
                                              String instruction, boolean allowDestructiveChanges, String userId) {
        DiagramEntity entity = load(diagramId, userId);
        if (baseVersion >= 0 && entity.getVersion() != baseVersion)
            throw new VersionConflictException("El diagrama cambió desde que se generó la vista previa.");
        UmlDiagram context = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        byte[] image = readImage(file);
        ImageUpdateAiResponse response = ai.interpret(image, safeName(file), detectedMime(image), instruction,
                allowDestructiveChanges, context);
        List<String> warnings = new ArrayList<>(safe(response.warnings()));
        List<ImageUpdateProposal> proposals = new ArrayList<>();
        for (ImageUpdateProposal proposal : safe(response.operations())) {
            if (proposal == null || !TYPES.contains(proposal.type())) { warnings.add("Se descartó una operación de imagen no soportada."); continue; }
            if (proposal.isDestructive() && !allowDestructiveChanges) {
                warnings.add("Se descartó un cambio destructivo porque no está habilitado."); continue;
            }
            if (isRename(proposal) && !explicitRename(instruction)) {
                warnings.add("Se descartó un renombre sin instrucción explícita."); continue;
            }
            proposals.add(normalizeProposal(proposal, context, warnings));
        }
        return new ImageUpdatePreviewResponse(response.success(), diagramId, entity.getVersion(), proposals, warnings, response.confidence());
    }

    @Transactional
    public DiagramUpdateResult apply(ImageUpdateApplyRequest request, String userId) {
        DiagramEntity entity = load(request.diagramId(), userId);
        if (entity.getVersion() != request.baseVersion())
            throw new VersionConflictException("El diagrama cambió desde que se generó la vista previa. Vuelve a analizar la imagen.");
        UmlDiagram current = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        DiagramViewState view = serializer.deserializeViewState(entity.getViewStateJson());
        UmlDiagram working = cloner.cloneDiagram(current);
        DiagramViewState workingView = cloner.cloneViewState(view);
        int applied = 0;
        for (ImageUpdateSelectedProposal selected : request.operations()) {
            if (selected == null || !selected.selected()) continue;
            ImageUpdateProposal proposal = selected.proposal();
            if (proposal == null) throw new OperationApplicationException("La propuesta seleccionada es inválida.");
            if (proposal.isDestructive() && !request.allowDestructiveChanges())
                throw new OperationApplicationException("Los cambios destructivos no están habilitados.");
            if (affectsAssociationClass(proposal, working))
                throw new OperationApplicationException("La operación afecta una clase de asociación y requiere revisión manual.");
            DiagramOperation operation = toOperation(proposal, working, request.diagramId(), current.getVersion());
            applier.apply(operation, working, workingView);
            applied++;
        }
        var validation = validator.validate(working);
        if (!validation.isValid()) throw new OperationApplicationException("Los cambios producirían un modelo UML inválido.");
        long newVersion = current.getVersion() + 1;
        working.setVersion(newVersion);
        entity.setCanonicalModelJson(serializer.serializeCanonical(working));
        entity.setViewStateJson(serializer.serializeViewState(workingView));
        entity.setVersion(newVersion);
        entity.setUpdatedAt(LocalDateTime.now());
        diagrams.save(entity);
        broadcaster.broadcastSnapshot(request.diagramId(), userId, "IMAGE_UPDATE", working, workingView);
        return new DiagramUpdateResult(request.diagramId(), request.baseVersion(), newVersion, working, workingView, applied);
    }

    private DiagramEntity load(String id, String userId) {
        DiagramEntity entity = diagrams.findById(id).orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + id));
        access.requireDiagramEditor(id, userId);
        return entity;
    }

    private DiagramOperation toOperation(ImageUpdateProposal p, UmlDiagram d, String diagramId, long version) {
        DiagramOperation.DiagramOperationBuilder builder = DiagramOperation.builder().operationId(UUID.randomUUID().toString())
                .diagramId(diagramId).userId(null).baseVersion(version);
        String type = p.type();
        switch (type) {
            case "CREATE_CLASS" -> { builder.type(DiagramOperationType.CREATE_CLASS).payload(CreateClassPayload.builder()
                    .classId(UUID.randomUUID().toString()).name(required(p.newClassName() != null ? p.newClassName() : p.className()))
                    .isAbstract(false).x(100 + d.getClasses().size() * 40).y(100).width(240).height(180).build()); }
            case "DELETE_CLASS" -> { builder.type(DiagramOperationType.DELETE_CLASS).payload(new DeleteClassPayload(findClass(d, p.className()).getId())); }
            case "RENAME_CLASS" -> { builder.type(DiagramOperationType.RENAME_CLASS).payload(new RenameClassPayload(findClass(d, p.className()).getId(), required(p.newClassName()))); }
            case "ADD_ATTRIBUTE" -> { builder.type(DiagramOperationType.ADD_ATTRIBUTE).payload(new AddAttributePayload(findClass(d, p.className()).getId(), UmlAttribute.builder().id(UUID.randomUUID().toString()).name(required(p.attributeName())).type(required(p.attributeType())).visibility(Visibility.PRIVATE).build())); }
            case "RENAME_ATTRIBUTE", "CHANGE_ATTRIBUTE_TYPE" -> { UmlClass c = findClass(d, p.className()); UmlAttribute a = findAttribute(c, p.attributeName()); builder.type(DiagramOperationType.UPDATE_ATTRIBUTE).payload(UpdateAttributePayload.builder().classId(c.getId()).attributeId(a.getId()).name("RENAME_ATTRIBUTE".equals(type) ? required(p.newAttributeName()) : a.getName()).type("CHANGE_ATTRIBUTE_TYPE".equals(type) ? required(p.attributeType()) : a.getType()).visibility(a.getVisibility()).isStatic(a.isStatic()).isFinal(a.isFinal()).defaultValue(a.getDefaultValue()).primaryKey(a.isPrimaryKey()).build()); }
            case "REMOVE_ATTRIBUTE" -> { UmlClass c = findClass(d, p.className()); builder.type(DiagramOperationType.REMOVE_ATTRIBUTE).payload(new RemoveAttributePayload(c.getId(), findAttribute(c, p.attributeName()).getId())); }
            case "ADD_METHOD" -> { builder.type(DiagramOperationType.ADD_METHOD).payload(new AddMethodPayload(findClass(d, p.className()).getId(), UmlMethod.builder().id(UUID.randomUUID().toString()).name(required(p.methodName())).returnType(p.returnType() == null ? "void" : p.returnType()).visibility(Visibility.PUBLIC).build())); }
            case "RENAME_METHOD" -> { UmlClass c = findClass(d, p.className()); UmlMethod m = findMethod(c, p.methodName()); builder.type(DiagramOperationType.UPDATE_METHOD).payload(UpdateMethodPayload.builder().classId(c.getId()).methodId(m.getId()).name(required(p.newMethodName())).returnType(m.getReturnType()).visibility(m.getVisibility()).isStatic(m.isStatic()).build()); }
            case "REMOVE_METHOD" -> { UmlClass c = findClass(d, p.className()); builder.type(DiagramOperationType.REMOVE_METHOD).payload(new RemoveMethodPayload(c.getId(), findMethod(c, p.methodName()).getId())); }
            case "CREATE_RELATION" -> { UmlClass source = findClass(d, first(p)), target = findClass(d, second(p)); builder.type(DiagramOperationType.CREATE_RELATION).payload(new CreateRelationPayload(UmlRelation.builder().id(UUID.randomUUID().toString()).sourceClassId(source.getId()).targetClassId(target.getId()).type(RelationType.valueOf(required(p.relationType()).toUpperCase(Locale.ROOT))).sourceMultiplicity(new Multiplicity("1", "1")).targetMultiplicity(new Multiplicity("1", "1")).build())); }
            case "REMOVE_RELATION", "CHANGE_RELATION_TYPE" -> { UmlRelation r = findRelation(d, p); if ("REMOVE_RELATION".equals(type)) builder.type(DiagramOperationType.DELETE_RELATION).payload(new DeleteRelationPayload(r.getId())); else builder.type(DiagramOperationType.CHANGE_RELATION_TYPE).payload(new ChangeRelationTypePayload(r.getId(), RelationType.valueOf(required(p.relationType()).toUpperCase(Locale.ROOT)))); }
            default -> throw new OperationApplicationException("Operación no soportada: " + type);
        }
        return builder.build();
    }

    private ImageUpdateProposal normalizeProposal(ImageUpdateProposal p, UmlDiagram d, List<String> warnings) {
        String attributeName = requiresExistingAttribute(p.type())
                ? canonicalAttributeName(d, p.className(), p.attributeName())
                : p.attributeName();
        String methodName = requiresExistingMethod(p.type())
                ? canonicalMethodName(d, p.className(), p.methodName())
                : p.methodName();
        return new ImageUpdateProposal(p.proposalId() == null ? UUID.randomUUID().toString() : p.proposalId(), p.type(),
                canonicalName(d, p.className()), canonicalName(d, p.secondaryClassName()), canonicalName(d, p.sourceClassName()), canonicalName(d, p.targetClassName()),
                p.newClassName(), attributeName, p.newAttributeName(), p.attributeType(), methodName, p.newMethodName(), p.returnType(), p.relationType(), p.confidence(), p.summary(), p.destructive(), p.warnings());
    }

    private boolean affectsAssociationClass(ImageUpdateProposal p, UmlDiagram d) { return ("REMOVE_RELATION".equals(p.type()) || "CHANGE_RELATION_TYPE".equals(p.type())) && d.getAssociationClassLinks().stream().anyMatch(l -> l.getRelationId().equals(findRelation(d, p).getId())); }
    private UmlRelation findRelation(UmlDiagram d, ImageUpdateProposal p) { String a = first(p), b = second(p); UmlClass c1 = findClass(d, a), c2 = findClass(d, b); return d.getRelations().stream().filter(r -> (r.getSourceClassId().equals(c1.getId()) && r.getTargetClassId().equals(c2.getId())) || (r.getSourceClassId().equals(c2.getId()) && r.getTargetClassId().equals(c1.getId()))).findFirst().orElseThrow(() -> new OperationApplicationException("No existe una relación entre las clases indicadas.")); }
    private UmlClass findClass(UmlDiagram d, String name) { String n = norm(name); return d.getClasses().stream().filter(c -> norm(c.getName()).equals(n)).findFirst().orElseThrow(() -> new OperationApplicationException("No existe la clase: " + name)); }
    private UmlAttribute findAttribute(UmlClass c, String name) { return c.getAttributes().stream().filter(a -> norm(a.getName()).equals(norm(name))).findFirst().orElseThrow(() -> new OperationApplicationException("No existe el atributo: " + name)); }
    private UmlMethod findMethod(UmlClass c, String name) { return c.getMethods().stream().filter(m -> norm(m.getName()).equals(norm(name))).findFirst().orElseThrow(() -> new OperationApplicationException("No existe el método: " + name)); }
    private String canonicalAttributeName(UmlDiagram d, String className, String name) {
        if (name == null || className == null) return name;
        return findAttribute(findClass(d, className), name).getName();
    }
    private String canonicalMethodName(UmlDiagram d, String className, String name) {
        if (name == null || className == null) return name;
        return findMethod(findClass(d, className), name).getName();
    }
    private String canonicalName(UmlDiagram d, String name) { return name == null ? null : d.getClasses().stream().filter(c -> norm(c.getName()).equals(norm(name))).map(UmlClass::getName).findFirst().orElse(name.trim()); }
    private String first(ImageUpdateProposal p) { return p.className() != null ? p.className() : p.sourceClassName(); }
    private String second(ImageUpdateProposal p) { return p.secondaryClassName() != null ? p.secondaryClassName() : p.targetClassName(); }
    private String norm(String s) { return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT); }
    private boolean isRename(ImageUpdateProposal p) { return Set.of("RENAME_CLASS", "RENAME_ATTRIBUTE", "RENAME_METHOD").contains(p.type()); }
    private boolean requiresExistingAttribute(String type) { return Set.of("RENAME_ATTRIBUTE", "CHANGE_ATTRIBUTE_TYPE", "REMOVE_ATTRIBUTE").contains(type); }
    private boolean requiresExistingMethod(String type) { return Set.of("RENAME_METHOD", "REMOVE_METHOD").contains(type); }
    private boolean explicitRename(String instruction) { String n = norm(instruction); return n.contains("renombr") || n.contains("cambia el nombre") || n.contains("cambiar el nombre"); }
    private String required(String value) { if (value == null || value.isBlank()) throw new OperationApplicationException("Faltan datos para la operación."); return value.trim(); }
    private byte[] readImage(MultipartFile file) { if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new IllegalArgumentException("La imagen es inválida o supera 10 MB."); try { return file.getBytes(); } catch (IOException e) { throw new IllegalArgumentException("No se pudo leer la imagen.", e); } }
    private String safeName(MultipartFile f) { String n = f.getOriginalFilename() == null ? "diagram" : f.getOriginalFilename().replace('\\', '/'); return n.substring(n.lastIndexOf('/') + 1); }
    private String detectedMime(MultipartFile f) { String n = safeName(f).toLowerCase(Locale.ROOT); return n.endsWith(".webp") ? "image/webp" : n.endsWith(".png") ? "image/png" : "image/jpeg"; }
    private String detectedMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && new String(bytes, 0, 4).equals("RIFF") && new String(bytes, 8, 4).equals("WEBP")) return "image/webp";
        throw new IllegalArgumentException("El archivo no contiene una imagen PNG, JPG o WEBP valida.");
    }
    private <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
    public record DiagramUpdateResult(String diagramId, long previousVersion, long newVersion, UmlDiagram canonicalModel, DiagramViewState viewState, int appliedOperations) {}
}
