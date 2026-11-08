package com.sw1.umltool.features.ai.analysis.service;

import com.sw1.umltool.features.ai.analysis.exception.AiAnalysisException;
import com.sw1.umltool.features.ai.chat.service.AiOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AiAnalysisServiceTest {
    private AiAnalysisContextService context;
    private AiOrchestrator orchestrator;
    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        context = mock(AiAnalysisContextService.class);
        orchestrator = mock(AiOrchestrator.class);
        service = new AiAnalysisService(context, orchestrator, new ObjectMapper(), 2);
    }

    @Test
    void returnsStructuredFindingsAndKeepsOnlyKnownElementIds() throws Exception {
        var prepared = new AiAnalysisContextService.PreparedContext("project-1", "context", Set.of("class-1"));
        when(context.prepare("project-1", "user-1")).thenReturn(prepared);
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Revisión completada\",\"findings\":[{" +
                        "\"severity\":\"WARNING\",\"category\":\"MULTIPLICITY\",\"elementId\":\"class-1\"," +
                        "\"title\":\"Falta multiplicidad\",\"description\":\"Revisar los extremos\",\"suggestion\":\"Definirla\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals("project-1", response.projectId());
        assertEquals(1, response.findings().size());
        assertEquals("class-1", response.findings().get(0).elementId());
    }

    @Test
    void discardsUnknownElementReferencesWithoutFailingAnalysis() throws Exception {
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", Set.of("class-1")));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"x\",\"findings\":[{" +
                        "\"severity\":\"ERROR\",\"category\":\"GENERAL\",\"elementId\":\"missing\"," +
                        "\"title\":\"x\",\"description\":\"x\",\"suggestion\":\"x\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
        assertEquals("No se encontraron hallazgos verificables.", response.summary());
    }

    @Test
    void keepsValidFindingWhenOneOfThreeFindingsIsInconsistent() throws Exception {
        var descriptors = Map.of(
                "class-1", new AiAnalysisContextService.ElementDescriptor(
                        "class-1", AiAnalysisContextService.ElementKind.CLASS, "Cliente"),
                "attribute-1", new AiAnalysisContextService.ElementDescriptor(
                        "attribute-1", AiAnalysisContextService.ElementKind.ATTRIBUTE, "apellido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Tres hallazgos\",\"findings\":["
                        + "{\"severity\":\"INFO\",\"category\":\"GENERAL\",\"elementId\":\"class-1\",\"title\":\"Clase revisada\",\"description\":\"La clase es válida\",\"suggestion\":null},"
                        + "{\"severity\":\"ERROR\",\"category\":\"GENERAL\",\"elementId\":\"missing\",\"title\":\"Elemento inválido\",\"description\":\"No coincide\",\"suggestion\":null},"
                        + "{\"severity\":\"INFO\",\"category\":\"ATTRIBUTE\",\"elementId\":\"attribute-1\",\"title\":\"Atributo revisado\",\"description\":\"Tiene tipo\",\"suggestion\":null}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(2, response.findings().size());
        assertEquals("Se identificaron 2 hallazgos verificables: Clase revisada; Atributo revisado.", response.summary());
    }

    @Test
    void discardsFindingThatMentionsAnotherClassThanItsElementId() throws Exception {
        var descriptors = Map.of(
                "cliente-id", new AiAnalysisContextService.ElementDescriptor(
                        "cliente-id", AiAnalysisContextService.ElementKind.CLASS, "Cliente"),
                "pedido-id", new AiAnalysisContextService.ElementDescriptor(
                        "pedido-id", AiAnalysisContextService.ElementKind.CLASS, "Pedido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Inconsistente\",\"findings\":["
                        + "{\"severity\":\"WARNING\",\"category\":\"GENERAL\",\"elementId\":\"cliente-id\",\"title\":\"Pedido tiene un problema\",\"description\":\"Revisar Pedido\",\"suggestion\":null}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
    }

    @Test
    void filtersUnverifiablePluralClaimsAndSelfRenameSuggestions() {
        var descriptors = Map.of(
                "cliente-id", new AiAnalysisContextService.ElementDescriptor("cliente-id", AiAnalysisContextService.ElementKind.CLASS, "Cliente"),
                "pedido-id", new AiAnalysisContextService.ElementDescriptor("pedido-id", AiAnalysisContextService.ElementKind.CLASS, "Pedido"),
                "apellido-id", new AiAnalysisContextService.ElementDescriptor("apellido-id", AiAnalysisContextService.ElementKind.ATTRIBUTE, "apellido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Se detectaron problemas\",\"findings\":[" +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"cliente-id\",\"title\":\"Cliente es plural\",\"description\":\"El nombre de la clase está en plural\",\"suggestion\":\"Renombrar Cliente a Cliente\"}," +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"pedido-id\",\"title\":\"Pedido es plural\",\"description\":\"El nombre de la clase está en plural\",\"suggestion\":\"Renombrar Pedido a Pedido\"}," +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"apellido-id\",\"title\":\"apellido es plural\",\"description\":\"El atributo es plural\",\"suggestion\":\"Cambiarlo\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
        assertEquals("No se encontraron hallazgos verificables.", response.summary());
    }

    @Test
    void discardsUnverifiedPluralClaimsInsteadOfShowingThemAsFindings() {
        var descriptors = Map.of(
                "clientes-id", new AiAnalysisContextService.ElementDescriptor("clientes-id", AiAnalysisContextService.ElementKind.CLASS, "Clientes"),
                "pedidos-id", new AiAnalysisContextService.ElementDescriptor("pedidos-id", AiAnalysisContextService.ElementKind.CLASS, "Pedidos"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Posibles plurales\",\"findings\":[" +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"clientes-id\",\"title\":\"Clientes es plural\",\"description\":\"Convención de nombres de clases\",\"suggestion\":\"Revisar\"}," +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"pedidos-id\",\"title\":\"Pedidos es plural\",\"description\":\"Convención de nombres de clases\",\"suggestion\":\"Revisar\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
        assertEquals("No se encontraron hallazgos verificables.", response.summary());
    }

    @Test
    void discardsAttributeStyleFalsePositivesWhenOnlyClassConventionExists() {
        var descriptors = Map.of(
                "cliente-id", new AiAnalysisContextService.ElementDescriptor("cliente-id", AiAnalysisContextService.ElementKind.CLASS, "Cliente"),
                "apellido-id", new AiAnalysisContextService.ElementDescriptor("apellido-id", AiAnalysisContextService.ElementKind.ATTRIBUTE, "apellido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors, false));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Revisar nombres\",\"findings\":[" +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"apellido-id\",\"title\":\"apellido debe estar en PascalCase\",\"description\":\"La convenciÃ³n no estÃ¡ definida\",\"suggestion\":\"Cambiarlo\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
        assertEquals("No se encontraron hallazgos verificables.", response.summary());
    }

    @Test
    void preservesStructuralAttributeFindingWhenStyleIsNotTheClaim() {
        var descriptors = Map.of("attribute-id", new AiAnalysisContextService.ElementDescriptor(
                "attribute-id", AiAnalysisContextService.ElementKind.ATTRIBUTE, "apellido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors, false));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Atributo sin tipo\",\"findings\":[" +
                        "{\"severity\":\"ERROR\",\"category\":\"ATTRIBUTE\",\"elementId\":\"attribute-id\",\"title\":\"Falta el tipo\",\"description\":\"El atributo no define tipo UML\",\"suggestion\":\"Asignar un tipo\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(1, response.findings().size());
        assertEquals(com.sw1.umltool.features.ai.analysis.model.AnalysisSeverity.ERROR,
                response.findings().get(0).severity());
    }

    @Test
    void acceptsAttributeStyleRuleOnlyWhenProjectMemoryMakesItExplicit() {
        var descriptors = Map.of("attribute-id", new AiAnalysisContextService.ElementDescriptor(
                "attribute-id", AiAnalysisContextService.ElementKind.ATTRIBUTE, "Apellido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "atributos en snake_case", descriptors, true));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Revisar\",\"findings\":[" +
                        "{\"severity\":\"WARNING\",\"category\":\"NAMING\",\"elementId\":\"attribute-id\",\"title\":\"Apellido no usa snake_case\",\"description\":\"La regla de atributos estÃ¡ definida\",\"suggestion\":\"Usar apellido\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(1, response.findings().size());
        assertEquals(com.sw1.umltool.features.ai.analysis.model.AnalysisSeverity.WARNING,
                response.findings().get(0).severity());
    }

    @Test
    void treatsMissingRelationshipRolesAsOptionalWhenMultiplicitiesExist() throws Exception {
        String relationId = "relation-1";
        var descriptors = Map.of(relationId,
                new AiAnalysisContextService.ElementDescriptor(relationId,
                        AiAnalysisContextService.ElementKind.RELATION, "association",
                        true, true));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Revisar asociación\",\"findings\":[{" +
                        "\"severity\":\"WARNING\",\"category\":\"RELATIONSHIP\",\"elementId\":\"relation-1\"," +
                        "\"title\":\"Faltan roles\",\"description\":\"La relación no tiene nombre de rol\",\"suggestion\":\"Agregar roles\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
    }

    @Test
    void removesKnownInternalIdsFromUserFacingFindingText() throws Exception {
        String classId = "123e4567-e89b-12d3-a456-426614174000";
        var descriptors = Map.of(classId,
                new AiAnalysisContextService.ElementDescriptor(classId,
                        AiAnalysisContextService.ElementKind.CLASS, "Cliente"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Revisar Cliente\",\"findings\":[{" +
                        "\"severity\":\"INFO\",\"category\":\"GENERAL\",\"elementId\":\"" + classId + "\"," +
                        "\"title\":\"Revisar " + classId + "\",\"description\":\"Elemento " + classId + "\",\"suggestion\":\"Consultar\"}]}"));

        var finding = service.analyze("project-1", "user-1").findings().get(0);

        org.junit.jupiter.api.Assertions.assertFalse(finding.title().contains(classId));
        org.junit.jupiter.api.Assertions.assertTrue(finding.title().contains("Cliente"));
    }

    @Test
    void acceptsAValidEmptyAnalysis() throws Exception {
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", Set.of()));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Sin hallazgos\",\"findings\":[]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
    }

    @Test
    void discardsRecommendationThatTreatsConcreteClassWithoutMethodsAsInvalid() {
        var descriptors = Map.of(
                "cliente-id", new AiAnalysisContextService.ElementDescriptor(
                        "cliente-id", AiAnalysisContextService.ElementKind.CLASS, "Cliente",
                        false, false, false, false),
                "pedido-id", new AiAnalysisContextService.ElementDescriptor(
                        "pedido-id", AiAnalysisContextService.ElementKind.CLASS, "Pedido",
                        false, false, false, false));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Clases abstractas sin implementaciÃ³n\",\"findings\":[" +
                        "{\"severity\":\"WARNING\",\"category\":\"OPERATION\",\"elementId\":\"cliente-id\",\"title\":\"Clase sin mÃ©todos\",\"description\":\"La clase no abstracta carece de mÃ©todos\",\"suggestion\":\"Agregar un constructor\"}," +
                        "{\"severity\":\"WARNING\",\"category\":\"OPERATION\",\"elementId\":\"pedido-id\",\"title\":\"Clase sin mÃ©todos\",\"description\":\"La clase no abstracta carece de mÃ©todos\",\"suggestion\":\"Agregar un mÃ©todo\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
        assertEquals("No se encontraron hallazgos verificables.", response.summary());
    }

    @Test
    void buildsSummaryOnlyFromAcceptedFindings() {
        var descriptors = Map.of("attribute-id", new AiAnalysisContextService.ElementDescriptor(
                "attribute-id", AiAnalysisContextService.ElementKind.ATTRIBUTE, "apellido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"El diagrama tiene problemas de roles y nomenclatura\",\"findings\":[" +
                        "{\"severity\":\"ERROR\",\"category\":\"ATTRIBUTE\",\"elementId\":\"attribute-id\",\"title\":\"Falta el tipo\",\"description\":\"El atributo no define tipo UML\",\"suggestion\":\"Asignar un tipo\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals("Se identific\u00f3 un hallazgo verificable: Falta el tipo.", response.summary());
    }

    @Test
    void discardsSemanticAttributeRedundancyWithoutDeterministicEvidence() {
        var descriptors = Map.of(
                "cliente-id", new AiAnalysisContextService.ElementDescriptor(
                        "cliente-id", AiAnalysisContextService.ElementKind.CLASS, "Cliente"),
                "pedido-id", new AiAnalysisContextService.ElementDescriptor(
                        "pedido-id", AiAnalysisContextService.ElementKind.CLASS, "Pedido"));
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", descriptors));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"summary\":\"Posible redundancia\",\"findings\":[" +
                        "{\"severity\":\"INFO\",\"category\":\"CONSISTENCY\",\"elementId\":\"cliente-id\"," +
                        "\"title\":\"Posible redundancia semántica\",\"description\":\"Cliente y Pedido comparten atributos\",\"suggestion\":\"Revisar\"}]}"));

        var response = service.analyze("project-1", "user-1");

        assertEquals(0, response.findings().size());
        assertEquals("No se encontraron hallazgos verificables.", response.summary());
    }

    @Test
    void promptScopesClassNamingConventionToClassesAndRequiresEvidence() {
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext(
                        "project-1", "Cliente; Pedido; atributo apellido; memoria: clases en singular", Set.of()));
        when(orchestrator.submitPrompt(anyString(), eq("Cliente; Pedido; atributo apellido; memoria: clases en singular")))
                .thenReturn(CompletableFuture.completedFuture("{\"summary\":\"Sin hallazgos\",\"findings\":[]}"));

        service.analyze("project-1", "user-1");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).submitPrompt(prompt.capture(), eq("Cliente; Pedido; atributo apellido; memoria: clases en singular"));
        String systemPrompt = prompt.getValue();
        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(systemPrompt.contains("UML")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(systemPrompt.contains("PROJECT")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(systemPrompt.contains("evidencia")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(systemPrompt.contains("elementId")));
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        when(context.prepare("project-1", "user-1"))
                .thenReturn(new AiAnalysisContextService.PreparedContext("project-1", "context", Set.of()));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("not-json"));

        assertThrows(AiAnalysisException.class, () -> service.analyze("project-1", "user-1"));
    }
}
