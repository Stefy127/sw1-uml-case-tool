package com.sw1.umltool.features.ai.changeproposal.service;

import com.sw1.umltool.features.ai.analysis.exception.AiAnalysisException;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposal;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalRequest;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalResponse;
import com.sw1.umltool.features.ai.chat.service.AiOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AiChangeProposalService {
    private static final Logger log = LoggerFactory.getLogger(AiChangeProposalService.class);
    private static final int MAX_PROPOSALS = 3;
    private static final int MAX_TEXT = 700;
    private static final Set<String> OPERATIONS = Set.of("ADD_ATTRIBUTE", "RENAME_ATTRIBUTE", "CHANGE_ATTRIBUTE_TYPE");
    private static final Set<String> TYPES = Set.of(
            "string", "integer", "int", "long", "boolean", "double", "float", "date", "localdate",
            "datetime", "localdatetime", "time", "localtime", "instant", "offsetdatetime", "bigdecimal", "uuid");

    private final AiChangeProposalContextService context;
    private final AiOrchestrator orchestrator;
    private final ObjectMapper mapper;
    private final long timeoutSeconds;

    public AiChangeProposalService(AiChangeProposalContextService context, AiOrchestrator orchestrator,
                                   ObjectMapper mapper,
                                   @Value("${ai.analysis.timeout-seconds:120}") long timeoutSeconds) {
        this.context = context;
        this.orchestrator = orchestrator;
        this.mapper = mapper;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public AiChangeProposalResponse propose(String projectId, String userId, String diagramId,
                                            AiChangeProposalRequest request) {
        String instruction = request == null ? null : request.instruction();
        String targetElementId = request == null ? null : request.targetElementId();
        AiChangeProposalContextService.Snapshot snapshot = context.capture(projectId, userId, diagramId,
                instruction, targetElementId);
        var future = orchestrator.submitPrompt(systemPrompt(), snapshot.prompt());
        try {
            String raw = future.get(timeoutSeconds + 1, TimeUnit.SECONDS);
            if (!context.isCurrent(snapshot, userId)) {
                return empty(snapshot, true);
            }
            return validate(parse(raw), snapshot);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AiAnalysisException("Las propuestas de cambio superaron el tiempo permitido.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AiAnalysisException("Las propuestas de cambio fueron interrumpidas.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            log.warn("AI change proposals failed stage=inference projectId={} diagramId={} errorType={}",
                    snapshot.projectId(), snapshot.diagramId(), cause.getClass().getSimpleName());
            throw new AiAnalysisException("Ollama no pudo generar propuestas de cambio.", cause);
        }
    }

    private JsonNode parse(String raw) {
        try {
            String json = raw == null ? "" : raw.trim();
            if (json.startsWith("```")) {
                int firstNewline = json.indexOf('\n');
                int lastFence = json.lastIndexOf("```");
                if (firstNewline < 0 || lastFence <= firstNewline) throw new IllegalArgumentException();
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject() || !node.has("proposals")) throw new IllegalArgumentException();
            return node;
        } catch (Exception exception) {
            throw new AiAnalysisException("La respuesta de propuestas no contiene JSON válido.", exception);
        }
    }

    private AiChangeProposalResponse validate(JsonNode root, AiChangeProposalContextService.Snapshot snapshot) {
        JsonNode values = root.get("proposals");
        if (values == null || !values.isArray() || values.size() > MAX_PROPOSALS) {
            throw new AiAnalysisException("La respuesta contiene demasiadas propuestas o un formato inválido.");
        }
        List<AiChangeProposal> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            JsonNode value = values.get(index);
            String operation = firstText(value, "operation", "type");
            if (operation == null) throw new AiAnalysisException("La propuesta no contiene operation.");
            operation = operation.toUpperCase(Locale.ROOT);
            if (!OPERATIONS.contains(operation)) {
                throw new AiAnalysisException("La operación de propuesta no está permitida.");
            }
            String classId = firstText(value, "targetElementId", "classId");
            AiChangeProposalContextService.ClassDescriptor target = snapshot.classById(classId);
            if (target == null) {
                String className = firstText(value, "targetElementName", "className");
                target = snapshot.classes().stream()
                        .filter(item -> sameName(item.name(), className))
                        .findFirst().orElse(null);
                if (target != null) classId = target.id();
            }
            if (target == null) throw new AiAnalysisException("La propuesta referencia una clase UML inexistente.");
            String attributeId = nullable(value, "targetAttributeId");
            String newName = firstText(value, "newAttributeName", "attributeName", "name");
            String newType = firstText(value, "attributeType", "newAttributeType");
            String reason = firstText(value, "reason", "explanation");
            if (reason == null) reason = "Propuesta generada a partir de la instrucción del usuario.";
            if (reason.length() > MAX_TEXT) reason = reason.substring(0, MAX_TEXT);
            AiChangeProposalContextService.AttributeDescriptor attribute = attributeId == null ? null
                    : target.attributes().stream().filter(item -> item.id().equals(attributeId)).findFirst().orElse(null);
            if (operation.equals("ADD_ATTRIBUTE")) {
                if (attributeId != null || !validName(newName) || !validType(newType)) {
                    throw new AiAnalysisException("La propuesta de atributo nuevo no tiene datos válidos.");
                }
                if (target.attributes().stream().anyMatch(item -> sameName(item.name(), newName))) {
                    throw new AiAnalysisException("La propuesta intenta duplicar un atributo existente.");
                }
                String key = operation + "|" + target.id() + "|" + newName.toLowerCase(Locale.ROOT) + "|" + newType.toLowerCase(Locale.ROOT);
                if (keys.add(key)) result.add(new AiChangeProposal(UUID.randomUUID().toString(), operation, target.id(), target.name(),
                        null, null, newName + ": " + newType, reason, value.path("requiresBusinessConfirmation").asBoolean(true)));
            } else {
                if (attribute == null || !validName(newName) && operation.equals("RENAME_ATTRIBUTE")
                        || !validType(newType) && operation.equals("CHANGE_ATTRIBUTE_TYPE")) {
                    throw new AiAnalysisException("La propuesta referencia un atributo inexistente o datos inválidos.");
                }
                if (operation.equals("RENAME_ATTRIBUTE")) {
                    if (sameName(attribute.name(), newName) || target.attributes().stream()
                            .anyMatch(item -> !item.id().equals(attribute.id()) && sameName(item.name(), newName))) {
                        throw new AiAnalysisException("El nuevo nombre del atributo no cambia el modelo o ya está ocupado.");
                    }
                    String key = operation + "|" + attribute.id() + "|" + newName.toLowerCase(Locale.ROOT);
                    if (keys.add(key)) result.add(new AiChangeProposal(UUID.randomUUID().toString(), operation, target.id(), target.name(),
                            attribute.id(), attribute.name(), newName, reason, value.path("requiresBusinessConfirmation").asBoolean(true)));
                } else {
                    if (attribute.type() != null && attribute.type().equalsIgnoreCase(newType)) {
                        throw new AiAnalysisException("El nuevo tipo del atributo no cambia el modelo.");
                    }
                    String key = operation + "|" + attribute.id() + "|" + newType.toLowerCase(Locale.ROOT);
                    if (keys.add(key)) result.add(new AiChangeProposal(UUID.randomUUID().toString(), operation, target.id(), target.name(),
                            attribute.id(), attribute.type(), newType, reason, value.path("requiresBusinessConfirmation").asBoolean(true)));
                }
            }
        }
        return new AiChangeProposalResponse(snapshot.projectId(), snapshot.diagramId(), snapshot.diagramVersion(), List.copyOf(result), false);
    }

    private AiChangeProposalResponse empty(AiChangeProposalContextService.Snapshot snapshot, boolean stale) {
        return new AiChangeProposalResponse(snapshot.projectId(), snapshot.diagramId(), snapshot.diagramVersion(), List.of(), stale);
    }

    private String required(JsonNode node, String field) {
        String value = nullable(node, field);
        if (value == null) throw new AiAnalysisException("La propuesta no contiene " + field + ".");
        return value;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = nullable(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private String nullable(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private boolean validType(String value) { return value != null && TYPES.contains(value.trim().toLowerCase(Locale.ROOT)); }

    private boolean validName(String value) {
        if (value == null || value.isBlank() || value.length() > 100) return false;
        String candidate = value.trim();
        if (!Character.isJavaIdentifierStart(candidate.charAt(0))) return false;
        for (int i = 1; i < candidate.length(); i++) if (!Character.isJavaIdentifierPart(candidate.charAt(i))) return false;
        return true;
    }

    private boolean sameName(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private String systemPrompt() {
        String contextualGuidance = "Si la instruccion pide sugerir atributos, completar o explorar una clase y existe una CLASE OBJETIVO SELECCIONADA, "
                + "propone como maximo tres atributos concretos, condicionados a requisitos del negocio. "
                + "Revisa sus atributos existentes, no repitas ninguno, usa tipos soportados y explica brevemente la utilidad. "
                + "Si falta contexto suficiente, devuelve cero propuestas o pide aclaracion; una clase vacia no es un error. "
                + "No propongas metodos ni relaciones solo porque falten. ";
        return "Eres SW1 IA y solo preparas propuestas explícitas de cambios UML para revisión humana. "
                + "No ejecutes ni persistas cambios. Devuelve únicamente JSON con {\"proposals\":[...]}. "
                + "Operaciones permitidas: ADD_ATTRIBUTE, RENAME_ATTRIBUTE, CHANGE_ATTRIBUTE_TYPE. "
                + "No propongas clases, métodos, relaciones, eliminaciones, abstracciones ni cambios espontáneos. "
                + "Una pregunta vaga como qué falta puede devolver cero propuestas. No inventes IDs ni atributos existentes; usa exactamente IDs del modelo. "
                + "Cada propuesta debe tener operation, targetElementId (ID de clase), targetAttributeId (ID existente para rename/type, null para add), "
                + "newAttributeName cuando corresponda, attributeType cuando corresponda, reason breve y requiresBusinessConfirmation boolean. "
                + contextualGuidance
                + "Rechaza instrucciones ambiguas y no amplíes reglas de memorias PROJECT fuera de su alcance.";
    }
}
