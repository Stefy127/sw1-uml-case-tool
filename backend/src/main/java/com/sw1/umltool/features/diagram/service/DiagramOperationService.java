package com.sw1.umltool.features.diagram.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.DiagramOperationApplier;
import com.sw1.umltool.features.diagram.operation.DiagramStateCloner;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.diagram.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class DiagramOperationService {

    private final DiagramOperationApplier diagramOperationApplier;
    private final CanonicalModelValidator canonicalModelValidator;
    private final DiagramStateCloner diagramStateCloner;

    public DiagramOperationService(
            DiagramOperationApplier diagramOperationApplier,
            CanonicalModelValidator canonicalModelValidator,
            DiagramStateCloner diagramStateCloner) {
        this.diagramOperationApplier = diagramOperationApplier;
        this.canonicalModelValidator = canonicalModelValidator;
        this.diagramStateCloner = diagramStateCloner;
    }

    public OperationExecutionResult execute(
            DiagramOperation operation,
            UmlDiagram currentDiagram,
            DiagramViewState currentViewState) {
        validateBasicArguments(operation, currentDiagram, currentViewState);

        long previousVersion = currentDiagram.getVersion();
        if (operation.getBaseVersion() != previousVersion) {
            throw new VersionConflictException("Version conflict: current version " + previousVersion
                    + ", received version " + operation.getBaseVersion());
        }

        UmlDiagram workingDiagram = diagramStateCloner.cloneDiagram(currentDiagram);
        DiagramViewState workingViewState = diagramStateCloner.cloneViewState(currentViewState);

        diagramOperationApplier.apply(operation, workingDiagram, workingViewState);

        ValidationResult validation = canonicalModelValidator.validate(workingDiagram);
        if (!validation.isValid()) {
            String errorCodes = validation.getErrors().stream()
                    .map(error -> error.getCode())
                    .collect(Collectors.joining(", "));
            throw new OperationApplicationException(
                    "Operation would produce an invalid UML model. Errors: " + errorCodes);
        }

        workingDiagram.setVersion(previousVersion + 1);
        return OperationExecutionResult.builder()
                .operationId(operation.getOperationId())
                .diagramId(currentDiagram.getId())
                .previousVersion(previousVersion)
                .newVersion(previousVersion + 1)
                .diagram(workingDiagram)
                .viewState(workingViewState)
                .build();
    }

    private void validateBasicArguments(
            DiagramOperation operation,
            UmlDiagram currentDiagram,
            DiagramViewState currentViewState) {
        if (operation == null) throw new OperationApplicationException("Operation is required");
        if (currentDiagram == null) throw new OperationApplicationException("Current diagram is required");
        if (currentViewState == null) throw new OperationApplicationException("Current view state is required");
        if (isBlank(operation.getOperationId())) throw new OperationApplicationException("Operation id is required");
        if (isBlank(operation.getDiagramId())) throw new OperationApplicationException("Operation diagram id is required");
        if (operation.getType() == null) throw new OperationApplicationException("Operation type is required");
        if (operation.getPayload() == null) throw new OperationApplicationException("Operation payload is required");
        if (currentDiagram.getId() == null) throw new OperationApplicationException("Current diagram id is required");
        if (!operation.getDiagramId().equals(currentDiagram.getId())) {
            throw new OperationApplicationException("Operation diagram id does not match current diagram id");
        }
        if (currentViewState.getDiagramId() != null
                && !currentDiagram.getId().equals(currentViewState.getDiagramId())) {
            throw new OperationApplicationException("View state diagram id does not match current diagram id");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
