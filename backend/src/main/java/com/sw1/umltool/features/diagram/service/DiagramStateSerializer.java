package com.sw1.umltool.features.diagram.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

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
            UmlDiagram diagram = objectMapper.readValue(json, UmlDiagram.class);
            if (diagram.getClasses() == null) diagram.setClasses(new ArrayList<>());
            else diagram.setClasses(new ArrayList<>(diagram.getClasses()));
            if (diagram.getRelations() == null) diagram.setRelations(new ArrayList<>());
            else diagram.setRelations(new ArrayList<>(diagram.getRelations()));
            if (diagram.getAssociationClassLinks() == null) {
                diagram.setAssociationClassLinks(new ArrayList<>());
            } else diagram.setAssociationClassLinks(new ArrayList<>(diagram.getAssociationClassLinks()));
            return diagram;
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
            DiagramViewState viewState = objectMapper.readValue(json, DiagramViewState.class);
            if (viewState.getNodes() == null) viewState.setNodes(new ArrayList<>());
            else viewState.setNodes(new ArrayList<>(viewState.getNodes()));
            if (viewState.getRelations() == null) viewState.setRelations(new ArrayList<>());
            else viewState.setRelations(new ArrayList<>(viewState.getRelations()));
            return viewState;
        } catch (JacksonException exception) {
            throw new DiagramSerializationException("Could not deserialize diagram view state", exception);
        }
    }
}
