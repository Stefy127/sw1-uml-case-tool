package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.diagram.model.view.RelationViewState;
import com.sw1.umltool.features.diagram.operation.payload.AddAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.AddMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.AddParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeMultiplicityPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeNavigabilityPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationRolesPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationTypePayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateAssociationClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateAssociationClassLinkPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteAssociationClassLinkPayload;
import com.sw1.umltool.features.diagram.operation.payload.MoveClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.RemoveAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.RemoveMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.RemoveParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.RenameClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.ResizeClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.SetClassAbstractPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateClassStylePayload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

@Component
public class DiagramOperationApplier {

    public void apply(DiagramOperation operation, UmlDiagram diagram, DiagramViewState viewState) {
        if (operation == null || diagram == null || viewState == null) {
            throw new OperationApplicationException("Operation, diagram and view state are required");
        }
        if (operation.getType() == null) {
            throw new OperationApplicationException("Operation type is required");
        }
        if (operation.getPayload() == null) {
            throw new OperationApplicationException("Operation payload is required");
        }
        if (diagram.getAssociationClassLinks() == null) {
            diagram.setAssociationClassLinks(new ArrayList<>());
        }

        switch (operation.getType()) {
            case CREATE_CLASS -> createClass(payload(operation, CreateClassPayload.class), diagram, viewState);
            case DELETE_CLASS -> deleteClass(payload(operation, DeleteClassPayload.class), diagram, viewState);
            case RENAME_CLASS -> renameClass(payload(operation, RenameClassPayload.class), diagram);
            case SET_CLASS_ABSTRACT -> setClassAbstract(payload(operation, SetClassAbstractPayload.class), diagram);
            case ADD_ATTRIBUTE -> addAttribute(payload(operation, AddAttributePayload.class), diagram);
            case UPDATE_ATTRIBUTE -> updateAttribute(payload(operation, UpdateAttributePayload.class), diagram);
            case REMOVE_ATTRIBUTE -> removeAttribute(payload(operation, RemoveAttributePayload.class), diagram);
            case ADD_METHOD -> addMethod(payload(operation, AddMethodPayload.class), diagram);
            case UPDATE_METHOD -> updateMethod(payload(operation, UpdateMethodPayload.class), diagram);
            case REMOVE_METHOD -> removeMethod(payload(operation, RemoveMethodPayload.class), diagram);
            case ADD_PARAMETER -> addParameter(payload(operation, AddParameterPayload.class), diagram);
            case UPDATE_PARAMETER -> updateParameter(payload(operation, UpdateParameterPayload.class), diagram);
            case REMOVE_PARAMETER -> removeParameter(payload(operation, RemoveParameterPayload.class), diagram);
            case CREATE_RELATION -> createRelation(payload(operation, CreateRelationPayload.class), diagram, viewState);
            case DELETE_RELATION -> deleteRelation(payload(operation, DeleteRelationPayload.class), diagram, viewState);
            case CREATE_ASSOCIATION_CLASS -> createAssociationClass(payload(operation, CreateAssociationClassPayload.class), diagram, viewState);
            case CREATE_ASSOCIATION_CLASS_LINK -> createAssociationClassLink(payload(operation, CreateAssociationClassLinkPayload.class), diagram);
            case DELETE_ASSOCIATION_CLASS_LINK -> deleteAssociationClassLink(payload(operation, DeleteAssociationClassLinkPayload.class), diagram);
            case CHANGE_RELATION_TYPE -> changeRelationType(payload(operation, ChangeRelationTypePayload.class), diagram);
            case CHANGE_MULTIPLICITY -> changeMultiplicity(payload(operation, ChangeMultiplicityPayload.class), diagram);
            case CHANGE_RELATION_ROLES -> changeRelationRoles(payload(operation, ChangeRelationRolesPayload.class), diagram);
            case CHANGE_NAVIGABILITY -> changeNavigability(payload(operation, ChangeNavigabilityPayload.class), diagram);
            case MOVE_CLASS -> moveClass(payload(operation, MoveClassPayload.class), viewState);
            case RESIZE_CLASS -> resizeClass(payload(operation, ResizeClassPayload.class), viewState);
            case UPDATE_CLASS_STYLE -> updateClassStyle(payload(operation, UpdateClassStylePayload.class), viewState);
        }
    }

    private void createClass(CreateClassPayload payload, UmlDiagram diagram, DiagramViewState viewState) {
        if (payload.getClassId() == null || findOptional(diagram.getClasses(), payload.getClassId(), UmlClass::getId).isPresent()) {
            throw new OperationApplicationException("Class id already exists or is missing: " + payload.getClassId());
        }
        requireText(payload.getName(), "Class name is required");
        diagram.getClasses().add(UmlClass.builder().id(payload.getClassId()).name(payload.getName())
                .isAbstract(payload.isAbstract()).build());
        viewState.getNodes().add(NodeViewState.builder().classId(payload.getClassId()).x(payload.getX()).y(payload.getY())
                .width(payload.getWidth()).height(payload.getHeight()).build());
    }

    private void deleteClass(DeleteClassPayload payload, UmlDiagram diagram, DiagramViewState viewState) {
        findClass(diagram, payload.getClassId());
        diagram.getClasses().removeIf(umlClass -> Objects.equals(umlClass.getId(), payload.getClassId()));
        viewState.getNodes().removeIf(node -> Objects.equals(node.getClassId(), payload.getClassId()));
        List<String> relationIds = diagram.getRelations().stream()
                .filter(relation -> Objects.equals(relation.getSourceClassId(), payload.getClassId())
                        || Objects.equals(relation.getTargetClassId(), payload.getClassId()))
                .map(UmlRelation::getId).toList();
        diagram.getRelations().removeIf(relation -> relationIds.contains(relation.getId()));
        viewState.getRelations().removeIf(view -> relationIds.contains(view.getRelationId()));
        diagram.getAssociationClassLinks().removeIf(link -> relationIds.contains(link.getRelationId()));
    }

    private void renameClass(RenameClassPayload payload, UmlDiagram diagram) {
        requireText(payload.getName(), "Class name is required");
        findClass(diagram, payload.getClassId()).setName(payload.getName());
    }

    private void setClassAbstract(SetClassAbstractPayload payload, UmlDiagram diagram) {
        findClass(diagram, payload.getClassId()).setAbstract(payload.isAbstract());
    }

    private void addAttribute(AddAttributePayload payload, UmlDiagram diagram) {
        UmlClass umlClass = findClass(diagram, payload.getClassId());
        if (payload.getAttribute() == null) {
            throw new OperationApplicationException("Attribute is required");
        }
        if (findOptional(umlClass.getAttributes(), payload.getAttribute().getId(), UmlAttribute::getId).isPresent()) {
            throw new OperationApplicationException("Attribute id already exists: " + payload.getAttribute().getId());
        }
        umlClass.getAttributes().add(payload.getAttribute());
    }

    private void updateAttribute(UpdateAttributePayload payload, UmlDiagram diagram) {
        UmlAttribute attribute = findAttribute(findClass(diagram, payload.getClassId()), payload.getAttributeId());
        if (payload.getName() != null) attribute.setName(payload.getName());
        if (payload.getType() != null) attribute.setType(payload.getType());
        if (payload.getVisibility() != null) attribute.setVisibility(payload.getVisibility());
        if (payload.getIsStatic() != null) attribute.setStatic(payload.getIsStatic());
        if (payload.getIsFinal() != null) attribute.setFinal(payload.getIsFinal());
        if (payload.getDefaultValue() != null) attribute.setDefaultValue(payload.getDefaultValue());
        if (payload.getPrimaryKey() != null) attribute.setPrimaryKey(payload.getPrimaryKey());
    }

    private void removeAttribute(RemoveAttributePayload payload, UmlDiagram diagram) {
        UmlClass umlClass = findClass(diagram, payload.getClassId());
        findAttribute(umlClass, payload.getAttributeId());
        umlClass.getAttributes().removeIf(attribute -> Objects.equals(attribute.getId(), payload.getAttributeId()));
    }

    private void addMethod(AddMethodPayload payload, UmlDiagram diagram) {
        UmlClass umlClass = findClass(diagram, payload.getClassId());
        if (payload.getMethod() == null) throw new OperationApplicationException("Method is required");
        if (findOptional(umlClass.getMethods(), payload.getMethod().getId(), UmlMethod::getId).isPresent()) {
            throw new OperationApplicationException("Method id already exists: " + payload.getMethod().getId());
        }
        umlClass.getMethods().add(payload.getMethod());
    }

    private void updateMethod(UpdateMethodPayload payload, UmlDiagram diagram) {
        UmlMethod method = findMethod(findClass(diagram, payload.getClassId()), payload.getMethodId());
        if (payload.getName() != null) method.setName(payload.getName());
        if (payload.getReturnType() != null) method.setReturnType(payload.getReturnType());
        if (payload.getVisibility() != null) method.setVisibility(payload.getVisibility());
        if (payload.getIsStatic() != null) method.setStatic(payload.getIsStatic());
    }

    private void removeMethod(RemoveMethodPayload payload, UmlDiagram diagram) {
        UmlClass umlClass = findClass(diagram, payload.getClassId());
        findMethod(umlClass, payload.getMethodId());
        umlClass.getMethods().removeIf(method -> Objects.equals(method.getId(), payload.getMethodId()));
    }

    private void addParameter(AddParameterPayload payload, UmlDiagram diagram) {
        UmlMethod method = findMethod(findClass(diagram, payload.getClassId()), payload.getMethodId());
        if (payload.getParameter() == null) throw new OperationApplicationException("Parameter is required");
        if (findOptional(method.getParameters(), payload.getParameter().getId(), UmlParameter::getId).isPresent()) {
            throw new OperationApplicationException("Parameter id already exists: " + payload.getParameter().getId());
        }
        method.getParameters().add(payload.getParameter());
    }

    private void updateParameter(UpdateParameterPayload payload, UmlDiagram diagram) {
        UmlParameter parameter = findParameter(findMethod(findClass(diagram, payload.getClassId()), payload.getMethodId()), payload.getParameterId());
        if (payload.getName() != null) parameter.setName(payload.getName());
        if (payload.getType() != null) parameter.setType(payload.getType());
    }

    private void removeParameter(RemoveParameterPayload payload, UmlDiagram diagram) {
        UmlMethod method = findMethod(findClass(diagram, payload.getClassId()), payload.getMethodId());
        findParameter(method, payload.getParameterId());
        method.getParameters().removeIf(parameter -> Objects.equals(parameter.getId(), payload.getParameterId()));
    }

    private void createRelation(CreateRelationPayload payload, UmlDiagram diagram, DiagramViewState viewState) {
        UmlRelation relation = payload.getRelation();
        if (relation == null) throw new OperationApplicationException("Relation is required");
        if (relation.getId() == null || findOptional(diagram.getRelations(), relation.getId(), UmlRelation::getId).isPresent()) {
            throw new OperationApplicationException("Relation id already exists or is missing: " + relation.getId());
        }
        findClass(diagram, relation.getSourceClassId());
        findClass(diagram, relation.getTargetClassId());
        diagram.getRelations().add(relation);
        viewState.getRelations().add(RelationViewState.builder().relationId(relation.getId()).build());
    }

    private void deleteRelation(DeleteRelationPayload payload, UmlDiagram diagram, DiagramViewState viewState) {
        findRelation(diagram, payload.getRelationId());
        diagram.getRelations().removeIf(relation -> Objects.equals(relation.getId(), payload.getRelationId()));
        viewState.getRelations().removeIf(view -> Objects.equals(view.getRelationId(), payload.getRelationId()));
        diagram.getAssociationClassLinks().removeIf(link -> Objects.equals(link.getRelationId(), payload.getRelationId()));
    }

    private void changeRelationType(ChangeRelationTypePayload payload, UmlDiagram diagram) {
        UmlRelation relation = findRelation(diagram, payload.getRelationId());
        if (payload.getType() != RelationType.ASSOCIATION
                && diagram.getAssociationClassLinks().stream()
                .anyMatch(link -> Objects.equals(link.getRelationId(), relation.getId()))) {
            throw new OperationApplicationException("An association class requires an ASSOCIATION relation");
        }
        relation.setType(payload.getType());
    }

    private void createAssociationClass(CreateAssociationClassPayload payload, UmlDiagram diagram,
            DiagramViewState viewState) {
        UmlRelation relation = findRelation(diagram, payload.getRelationId());
        if (relation.getType() != RelationType.ASSOCIATION) {
            throw new OperationApplicationException("Association classes require an ASSOCIATION relation");
        }
        validateAssociationClassLinkIds(payload.getLinkId(), payload.getRelationId(), payload.getClassId(), diagram);
        requireText(payload.getName(), "Association class name is required");
        diagram.getClasses().add(UmlClass.builder().id(payload.getClassId()).name(payload.getName())
                .isAbstract(payload.isAbstract()).build());
        viewState.getNodes().add(NodeViewState.builder().classId(payload.getClassId()).x(payload.getX()).y(payload.getY())
                .width(payload.getWidth()).height(payload.getHeight()).build());
        diagram.getAssociationClassLinks().add(AssociationClassLink.builder().id(payload.getLinkId())
                .relationId(payload.getRelationId()).classId(payload.getClassId()).build());
    }

    private void createAssociationClassLink(CreateAssociationClassLinkPayload payload, UmlDiagram diagram) {
        UmlRelation relation = findRelation(diagram, payload.getRelationId());
        if (relation.getType() != RelationType.ASSOCIATION) {
            throw new OperationApplicationException("Association classes require an ASSOCIATION relation");
        }
        findClass(diagram, payload.getClassId());
        validateAssociationClassLinkIds(payload.getLinkId(), payload.getRelationId(), payload.getClassId(), diagram);
        diagram.getAssociationClassLinks().add(AssociationClassLink.builder().id(payload.getLinkId())
                .relationId(payload.getRelationId()).classId(payload.getClassId()).build());
    }

    private void deleteAssociationClassLink(DeleteAssociationClassLinkPayload payload, UmlDiagram diagram) {
        findOptional(diagram.getAssociationClassLinks(), payload.getLinkId(), AssociationClassLink::getId)
                .orElseThrow(() -> new OperationApplicationException("Association class link not found: " + payload.getLinkId()));
        diagram.getAssociationClassLinks().removeIf(link -> Objects.equals(link.getId(), payload.getLinkId()));
    }

    private void validateAssociationClassLinkIds(String linkId, String relationId, String classId, UmlDiagram diagram) {
        if (linkId == null || findOptional(diagram.getAssociationClassLinks(), linkId, AssociationClassLink::getId).isPresent()) {
            throw new OperationApplicationException("Association class link id already exists or is missing: " + linkId);
        }
        if (classId == null || findOptional(diagram.getClasses(), classId, UmlClass::getId).isPresent()) {
            throw new OperationApplicationException("Class id already exists or is missing: " + classId);
        }
        if (diagram.getAssociationClassLinks().stream().anyMatch(link -> Objects.equals(link.getRelationId(), relationId))) {
            throw new OperationApplicationException("The relation already has an association class");
        }
        if (diagram.getAssociationClassLinks().stream().anyMatch(link -> Objects.equals(link.getClassId(), classId))) {
            throw new OperationApplicationException("The class is already an association class");
        }
    }

    private void changeMultiplicity(ChangeMultiplicityPayload payload, UmlDiagram diagram) {
        UmlRelation relation = findRelation(diagram, payload.getRelationId());
        Multiplicity source = payload.getSourceMultiplicity();
        Multiplicity target = payload.getTargetMultiplicity();
        if (source != null) relation.setSourceMultiplicity(source);
        if (target != null) relation.setTargetMultiplicity(target);
    }

    private void changeRelationRoles(ChangeRelationRolesPayload payload, UmlDiagram diagram) {
        UmlRelation relation = findRelation(diagram, payload.getRelationId());
        if (payload.getSourceRole() != null) relation.setSourceRole(payload.getSourceRole());
        if (payload.getTargetRole() != null) relation.setTargetRole(payload.getTargetRole());
    }

    private void changeNavigability(ChangeNavigabilityPayload payload, UmlDiagram diagram) {
        UmlRelation relation = findRelation(diagram, payload.getRelationId());
        if (payload.getSourceNavigable() != null) relation.setSourceNavigable(payload.getSourceNavigable());
        if (payload.getTargetNavigable() != null) relation.setTargetNavigable(payload.getTargetNavigable());
    }

    private void moveClass(MoveClassPayload payload, DiagramViewState viewState) {
        NodeViewState node = findNodeViewState(viewState, payload.getClassId());
        node.setX(payload.getX());
        node.setY(payload.getY());
    }

    private void resizeClass(ResizeClassPayload payload, DiagramViewState viewState) {
        if (payload.getWidth() <= 0 || payload.getHeight() <= 0) {
            throw new OperationApplicationException("Class dimensions must be greater than zero");
        }
        NodeViewState node = findNodeViewState(viewState, payload.getClassId());
        node.setWidth(payload.getWidth());
        node.setHeight(payload.getHeight());
    }

    private void updateClassStyle(UpdateClassStylePayload payload, DiagramViewState viewState) {
        NodeViewState node = findNodeViewState(viewState, payload.getClassId());
        node.setHeaderColor(payload.getHeaderColor());
        node.setBodyColor(payload.getBodyColor());
        node.setBorderColor(payload.getBorderColor());
    }

    private UmlClass findClass(UmlDiagram diagram, String id) {
        return findOptional(diagram.getClasses(), id, UmlClass::getId)
                .orElseThrow(() -> new OperationApplicationException("Class not found: " + id));
    }

    private UmlAttribute findAttribute(UmlClass umlClass, String id) {
        return findOptional(umlClass.getAttributes(), id, UmlAttribute::getId)
                .orElseThrow(() -> new OperationApplicationException("Attribute not found: " + id));
    }

    private UmlMethod findMethod(UmlClass umlClass, String id) {
        return findOptional(umlClass.getMethods(), id, UmlMethod::getId)
                .orElseThrow(() -> new OperationApplicationException("Method not found: " + id));
    }

    private UmlParameter findParameter(UmlMethod method, String id) {
        return findOptional(method.getParameters(), id, UmlParameter::getId)
                .orElseThrow(() -> new OperationApplicationException("Parameter not found: " + id));
    }

    private UmlRelation findRelation(UmlDiagram diagram, String id) {
        return findOptional(diagram.getRelations(), id, UmlRelation::getId)
                .orElseThrow(() -> new OperationApplicationException("Relation not found: " + id));
    }

    private NodeViewState findNodeViewState(DiagramViewState viewState, String classId) {
        return findOptional(viewState.getNodes(), classId, NodeViewState::getClassId)
                .orElseThrow(() -> new OperationApplicationException("Node view state not found: " + classId));
    }

    private <T> T payload(DiagramOperation operation, Class<T> payloadType) {
        try {
            return payloadType.cast(operation.getPayload());
        } catch (ClassCastException exception) {
            throw new OperationApplicationException("Invalid payload for operation: " + operation.getType());
        }
    }

    private <T> java.util.Optional<T> findOptional(List<T> elements, String id, java.util.function.Function<T, String> idExtractor) {
        if (elements == null) throw new OperationApplicationException("Model collection is missing");
        return elements.stream().filter(element -> Objects.equals(idExtractor.apply(element), id)).findFirst();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new OperationApplicationException(message);
    }
}
