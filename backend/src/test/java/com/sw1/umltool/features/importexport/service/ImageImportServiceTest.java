package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.importexport.dto.AiUmlDetectionResponse;
import com.sw1.umltool.features.importexport.mapper.ImageUmlCanonicalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageImportServiceTest {
    private ImageUmlAiService ai;
    private ImageImportService service;

    @BeforeEach
    void setUp() {
        ai = mock(ImageUmlAiService.class);
        var detection = new AiUmlDetectionResponse(true,
                new AiUmlDetectionResponse.DiagramDetection(List.of(
                        new AiUmlDetectionResponse.ClassDetection("c", "Cliente", false, List.of(), List.of())),
                        List.of(), List.of()), List.of(), .9, "");
        when(ai.interpret(any(), anyString(), anyString())).thenReturn(detection);
        service = new ImageImportService(ai, new ImageUmlCanonicalMapper(), new CanonicalModelValidator(),
                mock(DiagramRepository.class), mock(DiagramStateSerializer.class));
    }

    @Test
    void acceptsPngJpegAndWebpByRealSignature() {
        assertEquals(1, service.preview(file("diagram.png", "image/png", png())).getStatistics().getClasses());
        assertEquals(1, service.preview(file("diagram.jpg", "image/jpeg", jpeg())).getStatistics().getClasses());
        assertEquals(1, service.preview(file("diagram.webp", "image/webp", webp())).getStatistics().getClasses());
        verify(ai, times(3)).interpret(any(), anyString(), anyString());
    }

    @Test
    void rejectsEmptyOversizedUnsupportedAndMismatchedFiles() {
        assertThrows(IllegalArgumentException.class, () -> service.preview(file("empty.png", "image/png", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> service.preview(file("huge.png", "image/png", new byte[(int) ImageImportService.MAX_SIZE + 1])));
        assertThrows(IllegalArgumentException.class, () -> service.preview(file("diagram.gif", "image/gif", png())));
        assertThrows(IllegalArgumentException.class, () -> service.preview(file("diagram.jpg", "image/jpeg", png())));
    }

    @Test
    void applyChecksBaseVersionAndPersistsCanonicalAndViewStateOnce() {
        DiagramRepository repository = mock(DiagramRepository.class);
        DiagramStateSerializer serializer = mock(DiagramStateSerializer.class);
        DiagramEntity entity = DiagramEntity.builder().id("d1").name("Modelo").version(4).build();
        when(repository.findById("d1")).thenReturn(Optional.of(entity));
        service = new ImageImportService(ai, new ImageUmlCanonicalMapper(), new CanonicalModelValidator(), repository, serializer);

        assertThrows(VersionConflictException.class, () -> service.apply("d1", 3, file("diagram.png", "image/png", png())));
        var result = service.apply("d1", 4, file("diagram.png", "image/png", png()));

        assertEquals(5, result.getCanonicalModel().getVersion());
        assertEquals(5, entity.getVersion());
        verify(repository, times(1)).save(entity);
        verify(serializer).serializeCanonical(result.getCanonicalModel());
        verify(serializer).serializeViewState(result.getViewState());
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) { return new MockMultipartFile("file", name, mime, bytes); }
    private byte[] png() { return new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}; }
    private byte[] jpeg() { return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}; }
    private byte[] webp() { return new byte[] {'R','I','F','F',0,0,0,0,'W','E','B','P'}; }
}
