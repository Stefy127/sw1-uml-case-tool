package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.operation.DiagramOperationApplier;
import com.sw1.umltool.features.diagram.operation.DiagramStateCloner;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.importexport.dto.ImageUpdateAiResponse;
import com.sw1.umltool.features.importexport.dto.ImageUpdatePreviewResponse;
import com.sw1.umltool.features.importexport.dto.ImageUpdateProposal;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUpdateServiceTest {

    @Mock private DiagramRepository diagrams;
    @Mock private DiagramStateSerializer serializer;
    @Mock private DiagramStateCloner cloner;
    @Mock private DiagramOperationApplier applier;
    @Mock private CanonicalModelValidator validator;
    @Mock private ImageUpdateAiService ai;
    @Mock private ProjectAccessService access;
    @Mock private CollaborationBroadcastService broadcaster;

    private ImageUpdateService service;
    private UmlDiagram context;

    @BeforeEach
    void setUp() {
        service = new ImageUpdateService(diagrams, serializer, cloner, applier, validator, ai, access, broadcaster);
        context = UmlDiagram.builder()
                .id("diagram-1")
                .version(4)
                .classes(List.of(
                        umlClass("class-client", "Cliente", attribute("client-id", "ID", "Long"), attribute("client-name", "Nombre", "String")),
                        umlClass("class-order", "Pedido", attribute("order-id", "ID", "Long"))))
                .build();
    }

    @Test
    void previewKeepsAddAttributeAsAddWhenAttributeDoesNotExistYet() {
        DiagramEntity entity = DiagramEntity.builder()
                .id("diagram-1")
                .projectId("project-1")
                .version(4)
                .canonicalModelJson("canonical")
                .viewStateJson("view")
                .build();
        ImageUpdateProposal proposal = new ImageUpdateProposal(
                null, "ADD_ATTRIBUTE", "Pedido", null, null, null, null,
                "Nombre", null, "String", null, null, null, null, 1.0,
                "Agregar Nombre:String a Pedido", false, List.of());

        when(diagrams.findById("diagram-1")).thenReturn(Optional.of(entity));
        when(serializer.deserializeCanonical("canonical")).thenReturn(context);
        when(ai.interpret(any(), eq("diagram.png"), eq("image/png"), eq(""), anyBoolean(), eq(context)))
                .thenReturn(new ImageUpdateAiResponse(true, List.of(proposal), List.of(), 1.0));

        ImageUpdatePreviewResponse preview = service.preview(
                "diagram-1", 4, new MockMultipartFile("file", "diagram.png", "image/png", pngSignature()), "", false, "user-1");

        assertTrue(preview.success());
        assertEquals(1, preview.operations().size());
        ImageUpdateProposal normalized = preview.operations().get(0);
        assertNotNull(normalized.proposalId());
        assertEquals("ADD_ATTRIBUTE", normalized.type());
        assertEquals("Pedido", normalized.className());
        assertEquals("Nombre", normalized.attributeName());
        assertEquals("String", normalized.attributeType());
    }

    private static UmlClass umlClass(String id, String name, UmlAttribute... attributes) {
        return UmlClass.builder().id(id).name(name).attributes(List.of(attributes)).build();
    }

    private static UmlAttribute attribute(String id, String name, String type) {
        return UmlAttribute.builder().id(id).name(name).type(type).visibility(Visibility.PRIVATE).build();
    }

    private static byte[] pngSignature() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};
    }
}
