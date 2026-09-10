package com.sw1.umltool.features.importexport.service;

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
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class XmiExportService {
    private static final Logger log = LoggerFactory.getLogger(XmiExportService.class);
    private static final String XMI = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML = "http://schema.omg.org/spec/UML/2.1";
    private final DiagramStateSerializer serializer;

    public XmiExportService(DiagramStateSerializer serializer) { this.serializer = serializer; }

    public byte[] export(String canonicalJson, String viewStateJson) {
        return export("unknown", canonicalJson, viewStateJson);
    }

    public byte[] export(String diagramId, String canonicalJson, String viewStateJson) {
        try {
            return exportInternal(diagramId, serializer.deserializeCanonical(canonicalJson), serializer.deserializeViewState(viewStateJson));
        } catch (XmiExportException exception) {
            log.error("XMI export failed for diagram {}", diagramId, exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("XMI export failed for diagram {}", diagramId, exception);
            throw new XmiExportException("No se pudo generar el archivo XMI", exception);
        }
    }

    public byte[] export(UmlDiagram diagram, DiagramViewState ignoredViewState) {
        try {
            return exportInternal(diagram == null ? "unknown" : diagram.getId(), diagram, ignoredViewState);
        } catch (XmiExportException exception) {
            String diagramId = diagram == null ? "unknown" : diagram.getId();
            log.error("XMI export failed for diagram {}", diagramId, exception);
            throw exception;
        } catch (RuntimeException exception) {
            String diagramId = diagram == null ? "unknown" : diagram.getId();
            log.error("XMI export failed for diagram {}", diagramId, exception);
            throw new XmiExportException("No se pudo generar el archivo XMI", exception);
        }
    }

    private byte[] exportInternal(String diagramId, UmlDiagram diagram, DiagramViewState ignoredViewState) {
        try {
            if (diagram == null) throw new IllegalArgumentException("El modelo canónico del diagrama es nulo");
            log.info("[XMI EXPORT] classes={} relations={} associationClasses={} diagram={}", diagram.getClasses().size(), diagram.getRelations().size(), diagram.getAssociationClassLinks() == null ? 0 : diagram.getAssociationClassLinks().size(), diagramId);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, StandardCharsets.UTF_8.name());
            Map<String, String> ids = new HashMap<>();
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            xml.writeStartElement("xmi", "XMI", XMI);
            xml.writeNamespace("xmi", "http://schema.omg.org/spec/XMI/2.1"); xml.writeNamespace("uml", "http://schema.omg.org/spec/UML/2.1");
            xml.writeAttribute("xmi", XMI, "version", "2.1");
            xml.writeStartElement("uml", "Model", UML);
            xml.writeAttribute("xmi", XMI, "id", xmiId(diagram.getId()));
            xmiType(xml, "uml:Model"); attr(xml, "name", "Model");
            xml.writeStartElement("packagedElement"); xmiType(xml, "uml:Package"); attr(xml, "id", xmiId(diagram.getId() + "package")); attr(xml, "name", safe(diagram.getName(), "Diagrama"));
            Map<String, UmlRelation> relationsById = new HashMap<>();
            for (UmlRelation relation : diagram.getRelations()) relationsById.put(relation.getId(), relation);
            Map<String, AssociationClassLink> associationClassesByClassId = new HashMap<>();
            var links = diagram.getAssociationClassLinks() == null ? List.<AssociationClassLink>of() : diagram.getAssociationClassLinks();
            for (AssociationClassLink link : links) associationClassesByClassId.put(link.getClassId(), link);
            for (UmlClass umlClass : diagram.getClasses()) ids.put(umlClass.getId(), xmiId(umlClass.getId()));
            for (UmlClass umlClass : diagram.getClasses()) {
                log.debug("[XMI EXPORT] class id={} name={}", umlClass.getId(), umlClass.getName());
                AssociationClassLink link = associationClassesByClassId.get(umlClass.getId());
                UmlRelation linkedRelation = link == null ? null : relationsById.get(link.getRelationId());
                if (link != null && linkedRelation == null) throw new IllegalArgumentException("AssociationClassLink relation does not exist: " + link.getRelationId());
                writeClass(xml, umlClass, ids.get(umlClass.getId()), linkedRelation, ids, diagram.getRelations());
            }
            log.info("[XMI EXPORT] relations={}", diagram.getRelations().size());
            for (UmlRelation relation : diagram.getRelations()) {
                log.debug("[XMI EXPORT] relation id={} type={} source={} target={}", relation.getId(), relation.getType(), relation.getSourceClassId(), relation.getTargetClassId());
                writeRelation(xml, relation, ids);
            }
            xml.writeEndElement(); xml.writeEndElement();
            log.info("[XMI EXPORT] associationClasses={}", links.size());
            if (!links.isEmpty()) { xml.writeStartElement("xmi", "Extension", XMI); attr(xml, "extender", "Enterprise Architect"); xml.writeStartElement("connectors"); for (AssociationClassLink link : links) { log.debug("[XMI EXPORT] associationClass linkId={} relationId={} classId={}", link.getId(), link.getRelationId(), link.getClassId()); var relation = diagram.getRelations().stream().filter(item -> item.getId().equals(link.getRelationId())).findFirst().orElse(null); if (relation != null) writeAssociationClassConnector(xml, relation, link, ids); else throw new IllegalArgumentException("AssociationClassLink referencia una relación inexistente: " + link.getRelationId()); } xml.writeEndElement(); xml.writeEndElement(); }
            if (diagram.getRelations().stream().anyMatch(item -> item.getType() == RelationType.AGGREGATION)) {
                xml.writeStartElement("xmi", "Extension", XMI); attr(xml, "extender", "Enterprise Architect"); xml.writeStartElement("connectors");
                for (UmlRelation relation : diagram.getRelations()) if (relation.getType() == RelationType.AGGREGATION) writeAggregationConnector(xml, relation, ids);
                xml.writeEndElement(); xml.writeEndElement();
            }
            if (diagram.getRelations().stream().anyMatch(item -> item.getType() == RelationType.INHERITANCE)) {
                xml.writeStartElement("xmi", "Extension", XMI); attr(xml, "extender", "Enterprise Architect"); xml.writeStartElement("connectors");
                for (UmlRelation relation : diagram.getRelations()) if (relation.getType() == RelationType.INHERITANCE) writeGeneralizationConnector(xml, relation, ids);
                xml.writeEndElement(); xml.writeEndElement();
            }
            log.info("[XMI EXPORT] writing XML diagram={} classes={} relations={} associationClasses={}", diagramId, diagram.getClasses().size(), diagram.getRelations().size(), links.size());
            xml.writeEndDocument(); xml.close();
            return output.toByteArray();
        } catch (XMLStreamException exception) {
            throw new XmiExportException("No se pudo generar el archivo XMI", exception);
        }
    }

    private void writeClass(XMLStreamWriter xml, UmlClass umlClass, String id, UmlRelation associationClassRelation, Map<String, String> ids, List<UmlRelation> relations) throws XMLStreamException {
        boolean associationClass = associationClassRelation != null;
        xml.writeStartElement("packagedElement"); xmiType(xml, associationClass ? "uml:AssociationClass" : "uml:Class"); attr(xml, "id", id); attr(xml, "name", safe(umlClass.getName(), "Clase"));
        if (umlClass.isAbstract()) attr(xml, "isAbstract", "true");
        if (associationClass) {
            String sourceEndId = xmiId(umlClass.getId() + "-association-source");
            String targetEndId = xmiId(umlClass.getId() + "-association-target");
            xml.writeStartElement("memberEnd"); attr(xml, "idref", sourceEndId); xml.writeEndElement();
            writeAssociationClassEnd(xml, sourceEndId, ids.get(associationClassRelation.getSourceClassId()), id);
            xml.writeStartElement("memberEnd"); attr(xml, "idref", targetEndId); xml.writeEndElement();
            writeAssociationClassEnd(xml, targetEndId, ids.get(associationClassRelation.getTargetClassId()), id);
        }
        for (UmlAttribute attribute : umlClass.getAttributes()) {
            xml.writeStartElement("ownedAttribute"); xmiType(xml, "uml:Property"); attr(xml, "id", xmiId(attribute.getId())); attr(xml, "name", safe(attribute.getName(), "atributo"));
            attr(xml, "visibility", (attribute.getVisibility() == null ? "package" : attribute.getVisibility().name().toLowerCase(Locale.ROOT))); attr(xml, "isStatic", Boolean.toString(attribute.isStatic())); attr(xml, "isReadOnly", Boolean.toString(attribute.isFinal()));
            if (attribute.getType() != null) attr(xml, "type", attribute.getType()); if (attribute.getDefaultValue() != null) attr(xml, "default", attribute.getDefaultValue()); xml.writeEndElement();
        }
        for (UmlMethod method : umlClass.getMethods()) {
            xml.writeStartElement("ownedOperation"); xmiType(xml, "uml:Operation"); attr(xml, "id", xmiId(method.getId())); attr(xml, "name", safe(method.getName(), "metodo")); attr(xml, "visibility", (method.getVisibility() == null ? "package" : method.getVisibility().name().toLowerCase(Locale.ROOT))); attr(xml, "isStatic", Boolean.toString(method.isStatic()));
            for (UmlParameter parameter : method.getParameters()) writeParameter(xml, parameter, false);
            writeParameter(xml, UmlParameter.builder().id(method.getId() + "return").name("return").type(method.getReturnType()).build(), true); xml.writeEndElement();
        }
        for (UmlRelation relation : relations) {
            if (relation.getType() == RelationType.INHERITANCE && umlClass.getId().equals(relation.getSourceClassId())) {
                String parentId = ids.get(relation.getTargetClassId());
                if (parentId == null) throw new IllegalArgumentException("Generalization references missing parent class");
                xml.writeStartElement("generalization"); xmiType(xml, "uml:Generalization"); attr(xml, "id", xmiId(relation.getId()));
                attr(xml, "specific", id); attr(xml, "general", parentId); xml.writeEndElement();
            }
        }
        xml.writeEndElement();
    }

    private void writeAssociationClassEnd(XMLStreamWriter xml, String id, String classId, String associationId) throws XMLStreamException {
        xml.writeStartElement("ownedEnd"); xmiType(xml, "uml:Property"); attr(xml, "id", id); attr(xml, "association", associationId);
        if (classId != null) { xml.writeStartElement("type"); attr(xml, "idref", classId); xml.writeEndElement(); }
        xml.writeEndElement();
    }

    private void writeParameter(XMLStreamWriter xml, UmlParameter parameter, boolean result) throws XMLStreamException {
        xml.writeStartElement("ownedParameter"); xmiType(xml, "uml:Parameter"); attr(xml, "id", xmiId(parameter.getId())); attr(xml, "name", safe(parameter.getName(), "parametro")); attr(xml, "direction", result ? "return" : "in"); if (parameter.getType() != null) attr(xml, "type", parameter.getType()); xml.writeEndElement();
    }

    private void writeRelation(XMLStreamWriter xml, UmlRelation relation, Map<String, String> ids) throws XMLStreamException {
        String source = ids.get(relation.getSourceClassId()), target = ids.get(relation.getTargetClassId());
        if (source == null || target == null) throw new IllegalArgumentException("La relación " + relation.getId() + " referencia clases inexistentes: " + relation.getSourceClassId() + " -> " + relation.getTargetClassId());
        if (relation.getType() == RelationType.INHERITANCE) return;
        if (relation.getType() == RelationType.DEPENDENCY) { directed(xml, "uml:Dependency", relation, "client", source, "supplier", target); return; }
        xml.writeStartElement("packagedElement"); xmiType(xml, "uml:Association"); attr(xml, "id", xmiId(relation.getId())); attr(xml, "name", "Association");
        xml.writeStartElement("memberEnd"); attr(xml, "idref", xmiId(relation.getId() + "source")); xml.writeEndElement();
        String sourceAggregation = relation.getType() == RelationType.COMPOSITION ? "composite" : relation.getType() == RelationType.AGGREGATION ? "none" : null;
        writeEnd(xml, relation.getId() + "source", source, relation.getSourceMultiplicity(), relation.getSourceRole(), sourceAggregation, relation.isSourceNavigable(), xmiId(relation.getId()));
        xml.writeStartElement("memberEnd"); attr(xml, "idref", xmiId(relation.getId() + "target")); xml.writeEndElement();
        String targetAggregation = relation.getType() == RelationType.AGGREGATION ? "shared" : relation.getType() == RelationType.COMPOSITION ? "none" : null;
        writeEnd(xml, relation.getId() + "target", target, relation.getTargetMultiplicity(), relation.getTargetRole(), targetAggregation, relation.isTargetNavigable(), xmiId(relation.getId())); xml.writeEndElement();
    }

    private void writeAssociationClassConnector(XMLStreamWriter xml, UmlRelation relation, AssociationClassLink link, Map<String, String> ids) throws XMLStreamException {
        if (ids.get(link.getClassId()) == null) throw new IllegalArgumentException("AssociationClassLink " + link.getId() + " referencia una clase inexistente: " + link.getClassId());
        xml.writeStartElement("connector"); attr(xml, "idref", xmiId(relation.getId()));
        xml.writeStartElement("source"); attr(xml, "idref", ids.get(relation.getSourceClassId()));
        xml.writeStartElement("type"); attr(xml, "multiplicity", multiplicityText(relation.getSourceMultiplicity())); xml.writeEndElement(); xml.writeEndElement();
        xml.writeStartElement("target"); attr(xml, "idref", ids.get(relation.getTargetClassId()));
        xml.writeStartElement("type"); attr(xml, "multiplicity", multiplicityText(relation.getTargetMultiplicity())); xml.writeEndElement(); xml.writeEndElement();
        xml.writeStartElement("properties"); attr(xml, "ea_type", "Association"); attr(xml, "subtype", "Class"); xml.writeEndElement();
        xml.writeStartElement("extendedProperties"); attr(xml, "associationclass", ids.get(link.getClassId())); xml.writeEndElement(); xml.writeEndElement();
    }

    private void writeGeneralizationConnector(XMLStreamWriter xml, UmlRelation relation, Map<String, String> ids) throws XMLStreamException {
        String source = ids.get(relation.getSourceClassId()), target = ids.get(relation.getTargetClassId());
        if (source == null || target == null) throw new IllegalArgumentException("Generalization references missing classes");
        xml.writeStartElement("connector"); attr(xml, "idref", xmiId(relation.getId()));
        xml.writeStartElement("source"); attr(xml, "idref", source); xml.writeEndElement();
        xml.writeStartElement("target"); attr(xml, "idref", target); xml.writeEndElement();
        xml.writeStartElement("properties"); attr(xml, "ea_type", "Generalization"); xml.writeEndElement();
        xml.writeEndElement();
    }

    private void writeAggregationConnector(XMLStreamWriter xml, UmlRelation relation, Map<String, String> ids) throws XMLStreamException {
        String source = ids.get(relation.getSourceClassId()), target = ids.get(relation.getTargetClassId());
        if (source == null || target == null) throw new IllegalArgumentException("Aggregation references missing classes");
        xml.writeStartElement("connector"); attr(xml, "idref", xmiId(relation.getId()));
        writeAggregationConnectorEnd(xml, "source", source, relation.getSourceMultiplicity(), "none");
        writeAggregationConnectorEnd(xml, "target", target, relation.getTargetMultiplicity(), "shared");
        xml.writeStartElement("properties"); attr(xml, "ea_type", "Aggregation"); attr(xml, "direction", "Unspecified"); xml.writeEndElement();
        xml.writeEndElement();
    }

    private void writeAggregationConnectorEnd(XMLStreamWriter xml, String endpoint, String classId, Multiplicity multiplicity, String aggregation) throws XMLStreamException {
        xml.writeStartElement(endpoint); attr(xml, "idref", classId);
        xml.writeStartElement("type"); attr(xml, "multiplicity", multiplicityText(multiplicity)); attr(xml, "aggregation", aggregation); xml.writeEndElement();
        xml.writeEndElement();
    }

    private void directed(XMLStreamWriter xml, String type, UmlRelation relation, String first, String firstValue, String second, String secondValue) throws XMLStreamException { xml.writeStartElement("packagedElement"); xmiType(xml, type); attr(xml, "id", xmiId(relation.getId())); if (firstValue != null) attr(xml, first, firstValue); if (secondValue != null) attr(xml, second, secondValue); xml.writeEndElement(); }
    private void writeEnd(XMLStreamWriter xml, String id, String classId, Multiplicity multiplicity, String role, String aggregation, boolean navigable, String associationId) throws XMLStreamException { xml.writeStartElement("ownedEnd"); xmiType(xml, "uml:Property"); attr(xml, "id", xmiId(id)); attr(xml, "association", associationId); if (role != null) attr(xml, "name", role); if (aggregation != null) attr(xml, "aggregation", aggregation); if (navigable) attr(xml, "isNavigable", "true"); if (classId != null) { xml.writeStartElement("type"); attr(xml, "idref", classId); xml.writeEndElement(); } if (multiplicity != null) { xml.writeStartElement("lowerValue"); attr(xml, "value", safe(multiplicity.getLower(), "1")); xml.writeEndElement(); xml.writeStartElement("upperValue"); attr(xml, "value", safe(multiplicity.getUpper(), "1")); xml.writeEndElement(); } xml.writeEndElement(); }
    private void xmiType(XMLStreamWriter xml, String value) throws XMLStreamException { xml.writeAttribute("xmi", XMI, "type", value); }
    private void attr(XMLStreamWriter xml, String name, String value) throws XMLStreamException { if ("id".equals(name) || "idref".equals(name)) xml.writeAttribute("xmi", XMI, name, value); else xml.writeAttribute(name, value); }
    private String xmiId(String value) { return "EAID_" + safe(value, "generated").replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT); }
    private String multiplicityText(Multiplicity value) { if (value == null) return "1"; return safe(value.getLower(), "1").equals(safe(value.getUpper(), "1")) ? safe(value.getLower(), "1") : safe(value.getLower(), "1") + ".." + safe(value.getUpper(), "1"); }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
