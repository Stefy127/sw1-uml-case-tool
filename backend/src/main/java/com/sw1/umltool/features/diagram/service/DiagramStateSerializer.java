package com.sw1.umltool.features.diagram.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import org.springframework.stereotype.Service;

@Service
public class DiagramStateSerializer {

    private final ObjectMapper objectMapper;

    public DiagramStateSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public String serializeCanonical(UmlDiagram diagram) {
        try {
            return objectMapper.writeValueAsString(diagram);
        } catch (JacksonException exception) {
            throw new DiagramSerializationException("Could not serialize canonical diagram", exception);
        }
    }

    public UmlDiagram deserializeCanonical(String json) {
        try {
            return objectMapper.readValue(json, UmlDiagram.class);
        } catch (JacksonException exception) {
            throw new DiagramSerializationException("Could not deserialize canonical diagram", exception);
        }
    }

    public String serializeViewState(DiagramViewState viewState) {
        try {
            return objectMapper.writeValueAsString(viewState);
        } catch (JacksonException exception) {
            throw new DiagramSerializationException("Could not serialize diagram view state", exception);
        }
    }

    public DiagramViewState deserializeViewState(String json) {
        try {
            return objectMapper.readValue(json, DiagramViewState.class);
        } catch (JacksonException exception) {
            throw new DiagramSerializationException("Could not deserialize diagram view state", exception);
        }
    }
}
