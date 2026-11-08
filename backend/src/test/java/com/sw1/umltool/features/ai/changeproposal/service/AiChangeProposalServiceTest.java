package com.sw1.umltool.features.ai.changeproposal.service;

import com.sw1.umltool.features.ai.analysis.exception.AiAnalysisException;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalRequest;
import com.sw1.umltool.features.ai.chat.service.AiOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChangeProposalServiceTest {
    private AiChangeProposalContextService context;
    private AiOrchestrator orchestrator;
    private AiChangeProposalService service;
    private AiChangeProposalContextService.Snapshot snapshot;

    @BeforeEach
    void setUp() {
        context = mock(AiChangeProposalContextService.class);
        orchestrator = mock(AiOrchestrator.class);
        service = new AiChangeProposalService(context, orchestrator, new ObjectMapper(), 2);
        snapshot = new AiChangeProposalContextService.Snapshot("p1", "d1", 7, null, "canonical", List.of(
                new AiChangeProposalContextService.ClassDescriptor("pedido", "Pedido", List.of(
                        new AiChangeProposalContextService.AttributeDescriptor("id", "id", "Long"),
                        new AiChangeProposalContextService.AttributeDescriptor("nombre", "nombre", "String"))),
                new AiChangeProposalContextService.ClassDescriptor("cliente", "Cliente", List.of())));
        when(context.capture(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(snapshot);
        when(context.isCurrent(snapshot, "u1")).thenReturn(true);
    }

    @Test
    void acceptsExplicitAddAttributeWithoutMutatingTheModel() {
        model("{\"proposals\":[{\"operation\":\"ADD_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"targetAttributeId\":null,\"newAttributeName\":\"fecha\",\"attributeType\":\"Date\",\"reason\":\"La instrucción lo solicita\",\"requiresBusinessConfirmation\":true}]}");

        var response = service.propose("p1", "u1", "d1", new AiChangeProposalRequest("Agregar fecha Date a Pedido", null));

        assertEquals(1, response.proposals().size());
        assertEquals("ADD_ATTRIBUTE", response.proposals().get(0).operation());
        assertEquals("pedido", response.proposals().get(0).targetElementId());
        assertEquals("fecha: Date", response.proposals().get(0).newValue());
        assertFalse(response.isStale());
    }

    @Test
    void forwardsSelectedClassToTheAuthorizedContext() {
        model("{\"proposals\":[]}");
        service.propose("p1", "u1", "d1", new AiChangeProposalRequest("Sugerir atributos para Pedido", "pedido"));
        verify(context).capture("p1", "u1", "d1", "Sugerir atributos para Pedido", "pedido");
    }

    @Test
    void acceptsRenameOnlyWhenAttributeExistsAndNameChanges() {
        model("{\"proposals\":[{\"operation\":\"RENAME_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"targetAttributeId\":\"nombre\",\"newAttributeName\":\"nombreCompleto\",\"reason\":\"La instrucción lo solicita\"}]}");
        var response = service.propose("p1", "u1", "d1", new AiChangeProposalRequest("Renombrar nombre", null));
        assertEquals("nombre", response.proposals().get(0).oldValue());
        assertEquals("nombreCompleto", response.proposals().get(0).newValue());
    }

    @Test
    void acceptsChangingAttributeTypeOnlyWhenItDiffers() {
        model("{\"proposals\":[{\"operation\":\"CHANGE_ATTRIBUTE_TYPE\",\"targetElementId\":\"pedido\",\"targetAttributeId\":\"nombre\",\"attributeType\":\"Integer\",\"reason\":\"La instrucción lo solicita\"}]}");
        var response = service.propose("p1", "u1", "d1", new AiChangeProposalRequest("Cambiar tipo", null));
        assertEquals("String", response.proposals().get(0).oldValue());
        assertEquals("Integer", response.proposals().get(0).newValue());
    }

    @Test
    void returnsZeroForAmbiguousInstructionWhenModelReturnsZero() {
        model("{\"proposals\":[]}");
        assertTrue(service.propose("p1", "u1", "d1", new AiChangeProposalRequest("Qué le falta a Pedido?", null))
                .proposals().isEmpty());
    }

    @Test
    void acceptsUpToThreeOpenAttributeSuggestionsUsingSemanticAliases() {
        model("{\"proposals\":["
                + "{\"operation\":\"ADD_ATTRIBUTE\",\"className\":\"Pedido\",\"attributeName\":\"fecha\",\"newAttributeType\":\"Date\",\"explanation\":\"Permite registrar la fecha\"},"
                + "{\"operation\":\"ADD_ATTRIBUTE\",\"className\":\"Pedido\",\"attributeName\":\"total\",\"attributeType\":\"Double\",\"explanation\":\"Permite registrar el importe\"},"
                + "{\"operation\":\"ADD_ATTRIBUTE\",\"targetElementId\":\"cliente\",\"newAttributeName\":\"email\",\"attributeType\":\"String\",\"reason\":\"Permite contacto\"}]}" );

        var response = service.propose("p1", "u1", "d1",
                new AiChangeProposalRequest("Sugerime hasta 3 atributos para Pedido", "pedido"));

        assertEquals(3, response.proposals().size());
        assertEquals("pedido", response.proposals().get(0).targetElementId());
        assertEquals("fecha: Date", response.proposals().get(0).newValue());
        assertEquals("total: Double", response.proposals().get(1).newValue());
        assertEquals("email: String", response.proposals().get(2).newValue());
    }

    @Test
    void reportsInvalidModelJsonAsControlledAnalysisError() {
        model("No puedo devolver JSON");

        var exception = assertThrows(AiAnalysisException.class, () -> service.propose("p1", "u1", "d1",
                new AiChangeProposalRequest("Sugerime atributos para Pedido", "pedido")));

        assertTrue(exception.getMessage().contains("JSON"));
    }

    @Test
    void rejectsDisallowedOperationAndUnknownAttribute() {
        model("{\"proposals\":[{\"operation\":\"REMOVE_CLASS\",\"targetElementId\":\"pedido\",\"reason\":\"x\"}]}");
        assertThrows(AiAnalysisException.class, () -> service.propose("p1", "u1", "d1", new AiChangeProposalRequest("eliminar", null)));

        model("{\"proposals\":[{\"operation\":\"RENAME_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"targetAttributeId\":\"missing\",\"newAttributeName\":\"x\",\"reason\":\"x\"}]}");
        assertThrows(AiAnalysisException.class, () -> service.propose("p1", "u1", "d1", new AiChangeProposalRequest("renombrar", null)));
    }

    @Test
    void rejectsDuplicateAttributeAndRedundantRename() {
        model("{\"proposals\":[{\"operation\":\"ADD_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"newAttributeName\":\"Nombre\",\"attributeType\":\"String\",\"reason\":\"x\"}]}");
        assertThrows(AiAnalysisException.class, () -> service.propose("p1", "u1", "d1", new AiChangeProposalRequest("agregar", null)));

        model("{\"proposals\":[{\"operation\":\"RENAME_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"targetAttributeId\":\"nombre\",\"newAttributeName\":\"nombre\",\"reason\":\"x\"}]}");
        assertThrows(AiAnalysisException.class, () -> service.propose("p1", "u1", "d1", new AiChangeProposalRequest("renombrar", null)));
    }

    @Test
    void discardsAllProposalsWhenDiagramVersionChangesDuringInference() {
        model("{\"proposals\":[{\"operation\":\"ADD_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"newAttributeName\":\"fecha\",\"attributeType\":\"Date\",\"reason\":\"x\"}]}");
        when(context.isCurrent(snapshot, "u1")).thenReturn(false);
        var response = service.propose("p1", "u1", "d1", new AiChangeProposalRequest("agregar", null));
        assertTrue(response.isStale());
        assertTrue(response.proposals().isEmpty());
        assertEquals(7, response.diagramVersion());
    }

    @Test
    void rejectsUnsupportedAttributeType() {
        model("{\"proposals\":[{\"operation\":\"ADD_ATTRIBUTE\",\"targetElementId\":\"pedido\",\"newAttributeName\":\"x\",\"attributeType\":\"MoneyUnknown\",\"reason\":\"x\"}]}");
        assertThrows(AiAnalysisException.class, () -> service.propose("p1", "u1", "d1", new AiChangeProposalRequest("agregar", null)));
    }

    private void model(String json) {
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(json));
    }
}
