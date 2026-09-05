package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.diagram.model.view.RelationViewState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DiagramStateCloner {

    public UmlDiagram cloneDiagram(UmlDiagram diagram) {
        if (diagram == null) {
            return null;
        }

        List<UmlClass> classes = diagram.getClasses() == null ? null : diagram.getClasses().stream()
                .map(this::cloneClass).toList();
        List<UmlRelation> relations = diagram.getRelations() == null ? null : diagram.getRelations().stream()
                .map(this::cloneRelation).toList();
        List<AssociationClassLink> associationClassLinks = diagram.getAssociationClassLinks() == null
                ? new ArrayList<>()
                : diagram.getAssociationClassLinks().stream().map(this::cloneAssociationClassLink).toList();

        return UmlDiagram.builder()
                .id(diagram.getId())
                .name(diagram.getName())
                .version(diagram.getVersion())
                .classes(classes == null ? null : new ArrayList<>(classes))
                .relations(relations == null ? null : new ArrayList<>(relations))
                .associationClassLinks(new ArrayList<>(associationClassLinks))
                .build();
    }

    public DiagramViewState cloneViewState(DiagramViewState viewState) {
        if (viewState == null) {
            return null;
        }

        List<NodeViewState> nodes = viewState.getNodes() == null ? null : viewState.getNodes().stream()
                .map(this::cloneNode).toList();
        List<RelationViewState> relations = viewState.getRelations() == null ? null : viewState.getRelations().stream()
                .map(this::cloneRelationView).toList();

        return DiagramViewState.builder()
                .diagramId(viewState.getDiagramId())
                .nodes(nodes == null ? null : new ArrayList<>(nodes))
                .relations(relations == null ? null : new ArrayList<>(relations))
                .build();
    }

    private UmlClass cloneClass(UmlClass umlClass) {
        if (umlClass == null) {
            return null;
        }
        List<UmlAttribute> attributes = umlClass.getAttributes() == null ? null : umlClass.getAttributes().stream()
                .map(this::cloneAttribute).toList();
        List<UmlMethod> methods = umlClass.getMethods() == null ? null : umlClass.getMethods().stream()
                .map(this::cloneMethod).toList();
        return UmlClass.builder().id(umlClass.getId()).name(umlClass.getName()).isAbstract(umlClass.isAbstract())
                .attributes(attributes == null ? null : new ArrayList<>(attributes))
                .methods(methods == null ? null : new ArrayList<>(methods)).build();
    }

    private UmlAttribute cloneAttribute(UmlAttribute attribute) {
        if (attribute == null) return null;
        return UmlAttribute.builder().id(attribute.getId()).name(attribute.getName()).type(attribute.getType())
                .visibility(attribute.getVisibility()).isStatic(attribute.isStatic()).isFinal(attribute.isFinal())
                .defaultValue(attribute.getDefaultValue()).primaryKey(attribute.isPrimaryKey()).build();
    }

    private UmlMethod cloneMethod(UmlMethod method) {
        if (method == null) return null;
        List<UmlParameter> parameters = method.getParameters() == null ? null : method.getParameters().stream()
                .map(this::cloneParameter).toList();
        return UmlMethod.builder().id(method.getId()).name(method.getName()).returnType(method.getReturnType())
                .visibility(method.getVisibility()).isStatic(method.isStatic())
                .parameters(parameters == null ? null : new ArrayList<>(parameters)).build();
    }

    private UmlParameter cloneParameter(UmlParameter parameter) {
        if (parameter == null) return null;
        return UmlParameter.builder().id(parameter.getId()).name(parameter.getName()).type(parameter.getType()).build();
    }

    private UmlRelation cloneRelation(UmlRelation relation) {
        if (relation == null) return null;
        return UmlRelation.builder().id(relation.getId()).sourceClassId(relation.getSourceClassId())
                .targetClassId(relation.getTargetClassId()).type(relation.getType())
                .sourceMultiplicity(cloneMultiplicity(relation.getSourceMultiplicity()))
                .targetMultiplicity(cloneMultiplicity(relation.getTargetMultiplicity()))
                .sourceRole(relation.getSourceRole()).targetRole(relation.getTargetRole())
                .sourceNavigable(relation.isSourceNavigable()).targetNavigable(relation.isTargetNavigable()).build();
    }

    private Multiplicity cloneMultiplicity(Multiplicity multiplicity) {
        if (multiplicity == null) return null;
        return Multiplicity.builder().lower(multiplicity.getLower()).upper(multiplicity.getUpper()).build();
    }

    private AssociationClassLink cloneAssociationClassLink(AssociationClassLink link) {
        if (link == null) return null;
        return AssociationClassLink.builder().id(link.getId()).relationId(link.getRelationId())
                .classId(link.getClassId()).build();
    }

    private NodeViewState cloneNode(NodeViewState node) {
        if (node == null) return null;
        return NodeViewState.builder().classId(node.getClassId()).x(node.getX()).y(node.getY())
                .width(node.getWidth()).height(node.getHeight()).headerColor(node.getHeaderColor())
                .bodyColor(node.getBodyColor()).borderColor(node.getBorderColor()).build();
    }

    private RelationViewState cloneRelationView(RelationViewState relation) {
        if (relation == null) return null;
        return RelationViewState.builder().relationId(relation.getRelationId()).build();
    }
}
