package com.sw1.umltool.features.importexport.parser;

import com.sw1.umltool.features.diagram.model.canonical.*;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.importexport.dto.XmiImportPreviewResponse;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class XmiParser {
    public XmiImportPreviewResponse parse(byte[] content, String diagramId, String name) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("El archivo XMI está vacío");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            Element root = document.getDocumentElement();
            String rootName = root == null ? "" : local(root).toLowerCase(Locale.ROOT);
            if (root == null || (!"xmi".equals(rootName) && !"model".equals(rootName))) {
                throw new IllegalArgumentException("El archivo no contiene un modelo XMI/UML reconocible");
            }
            List<String> warnings = new ArrayList<>();
            Map<String, String> ids = new LinkedHashMap<>();
            Map<String, String> extensionTypes = extensionAttributeTypes(document);
            Map<String, String> namedTypes = namedTypes(document);
            List<UmlClass> classes = new ArrayList<>();
            List<NodeViewState> nodes = new ArrayList<>();
            List<Element> classElements = classElements(document);
            for (int i = 0; i < classElements.size(); i++) {
                Element element = classElements.get(i);
                String external = id(element);
                String classId = UUID.randomUUID().toString();
                if (external != null) ids.put(external, classId);
                UmlClass umlClass = UmlClass.builder().id(classId)
                        .name(value(element, "name", "ClaseImportada"))
                        .isAbstract(Boolean.parseBoolean(value(element, "isAbstract", "false")))
                        .build();
                classes.add(umlClass);
                int column = i % 4;
                int row = i / 4;
                nodes.add(NodeViewState.builder().classId(classId).x(100 + column * 320).y(100 + row * 240)
                        .width(240).height(180).build());
            }
            List<UmlRelation> relations = new ArrayList<>();
            Map<String, String> relationIds = new LinkedHashMap<>();
            Map<String, String> associationClassRelations = new LinkedHashMap<>();
            for (UmlClass umlClass : classes) {
                Element source = findByMappedId(document, ids, umlClass.getId());
                if (source == null) continue;
                for (Element attribute : children(source, "ownedAttribute", "Attribute", "attribute")) {
                    umlClass.getAttributes().add(UmlAttribute.builder().id(UUID.randomUUID().toString())
                            .name(value(attribute, "name", "attribute")).type(type(attribute, extensionTypes, namedTypes, warnings))
                            .visibility(visibility(attribute.getAttribute("visibility"))).isStatic(false)
                            .isFinal(false).primaryKey(false).build());
                }
                for (Element operation : children(source, "ownedOperation", "Operation", "operation")) {
                    String returnType = "void";
                    for (Element returnParameter : children(operation, "ownedParameter", "Parameter", "parameter")) {
                        if ("return".equals(returnParameter.getAttribute("direction"))) {
                            returnType = type(returnParameter, extensionTypes, namedTypes, warnings);
                            break;
                        }
                    }
                    UmlMethod method = UmlMethod.builder().id(UUID.randomUUID().toString())
                            .name(value(operation, "name", "method")).returnType(returnType)
                            .visibility(visibility(operation.getAttribute("visibility"))).isStatic(false).build();
                    for (Element parameter : children(operation, "ownedParameter", "Parameter", "parameter")) {
                        method.getParameters().add(UmlParameter.builder().id(UUID.randomUUID().toString())
                                .name(value(parameter, "name", "parameter")).type(type(parameter, extensionTypes, namedTypes, warnings)).build());
                    }
                    umlClass.getMethods().add(method);
                }
            }
            for (Element association : typedElements(document, "Association")) {
                if (isAssociationClassConnector(document, id(association))) continue;
                List<Element> ends = children(association, "ownedEnd", "Property", "property");
                if (ends.size() < 2) { warnings.add("Asociación sin dos extremos: " + value(association, "name", "sin nombre")); continue; }
                String[] connector = connectorEndpoints(document, id(association));
                Element sourceEnd = endForClass(ends, connector == null ? null : connector[0]);
                Element targetEnd = endForClass(ends, connector == null ? null : connector[1]);
                if (sourceEnd == null) sourceEnd = ends.get(0);
                if (targetEnd == null || targetEnd == sourceEnd) targetEnd = ends.get(1);
                String source = resolveReference(sourceEnd, ids);
                String target = resolveReference(targetEnd, ids);
                if (source == null || target == null) { warnings.add("Asociación con referencia de clase no resuelta"); continue; }
                RelationType type = "composite".equals(sourceEnd.getAttribute("aggregation")) ? RelationType.COMPOSITION
                        : "composite".equals(targetEnd.getAttribute("aggregation")) ? RelationType.COMPOSITION
                        : "shared".equals(sourceEnd.getAttribute("aggregation")) || "shared".equals(targetEnd.getAttribute("aggregation")) ? RelationType.AGGREGATION
                        : RelationType.ASSOCIATION;
                String relationId = UUID.randomUUID().toString();
                relations.add(UmlRelation.builder().id(relationId).sourceClassId(source).targetClassId(target).type(type)
                        .sourceMultiplicity(multiplicity(sourceEnd)).targetMultiplicity(multiplicity(targetEnd))
                        .sourceRole(emptyToNull(sourceEnd.getAttribute("name"))).targetRole(emptyToNull(targetEnd.getAttribute("name"))).build());
                relationIds.put(id(association), relationId);
            }
            for (Element connector : elements(document, "connector")) {
                String associationClassId = associationClassId(connector);
                if (associationClassId == null) continue;
                Element sourceElement = first(connector, "source");
                Element targetElement = first(connector, "target");
                String source = sourceElement == null ? null : resolveReference(sourceElement, ids);
                String target = targetElement == null ? null : resolveReference(targetElement, ids);
                if (source == null || target == null) {
                    warnings.add("Clase de asociaciÃ³n con extremos no resueltos");
                    continue;
                }
                String relationId = UUID.randomUUID().toString();
                relations.add(UmlRelation.builder().id(relationId).sourceClassId(source).targetClassId(target)
                        .type(RelationType.ASSOCIATION).sourceMultiplicity(connectorMultiplicity(sourceElement))
                        .targetMultiplicity(connectorMultiplicity(targetElement)).build());
                relationIds.put(associationClassId, relationId);
                associationClassRelations.put(associationClassId, relationId);
            }
            for (Element generalization : typedElements(document, "Generalization")) addRelation(relations, generalization, ids, RelationType.INHERITANCE, "specific", "general", warnings);
            for (Element dependency : typedElements(document, "Dependency")) addRelation(relations, dependency, ids, RelationType.DEPENDENCY, "client", "supplier", warnings);
            List<AssociationClassLink> links = new ArrayList<>();
            for (Map.Entry<String, String> entry : associationClassRelations.entrySet()) {
                String classId = ids.get(entry.getKey());
                if (classId != null) links.add(AssociationClassLink.builder().id(UUID.randomUUID().toString())
                        .relationId(entry.getValue()).classId(classId).build());
            }
            UmlDiagram diagram = UmlDiagram.builder().id(diagramId).name(name == null ? "Diagrama importado" : name).version(0)
                    .classes(classes).relations(relations).associationClassLinks(links).build();
            DiagramViewState view = DiagramViewState.builder().diagramId(diagramId).nodes(nodes).relations(new ArrayList<>()).build();
            return XmiImportPreviewResponse.builder().canonicalModel(diagram).viewState(view).warnings(warnings)
                    .statistics(XmiImportPreviewResponse.Statistics.builder().classes(classes.size())
                            .attributes(classes.stream().mapToInt(c -> c.getAttributes().size()).sum())
                            .methods(classes.stream().mapToInt(c -> c.getMethods().size()).sum()).relations(relations.size()).associationClasses(links.size()).build()).build();
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) { throw new IllegalArgumentException("No se pudo analizar el archivo XML/XMI", exception); }
    }

    private void addRelation(List<UmlRelation> result, Element e, Map<String,String> ids, RelationType type, String sourceKey, String targetKey, List<String> warnings) {
        String source = resolveReference(e, ids, sourceKey), target = resolveReference(e, ids, targetKey);
        if (source == null || target == null) { warnings.add("Relación " + type + " con referencia no resuelta"); return; }
        result.add(UmlRelation.builder().id(UUID.randomUUID().toString()).sourceClassId(source).targetClassId(target).type(type)
                .sourceMultiplicity(Multiplicity.builder().lower("0").upper("*").build()).targetMultiplicity(Multiplicity.builder().lower("0").upper("*").build()).build());
    }
    private Element findByMappedId(Document d, Map<String,String> ids, String mapped) { for (Element e : classElements(d)) if (mapped.equals(ids.get(id(e)))) return e; return null; }
    private String resolveReference(Element e, Map<String,String> ids) { return resolveReference(e, ids, "type"); }
    private String resolveReference(Element e, Map<String,String> ids, String key) { String v=e.getAttribute(key); Element child=first(e,key); if(v.isBlank()&&child!=null)v=externalReference(child); if(v.isBlank())v=externalReference(e); if(v.isBlank())v=e.getAttribute("href"); if(v.contains("#"))v=v.substring(v.indexOf('#')+1); return ids.get(v); }
    private Multiplicity multiplicity(Element e) { String value=e.getAttribute("multiplicity"); if(value.isBlank()){String lower=value(first(e,"lowerValue"),"value","0");String upper=value(first(e,"upperValue"),"value","1");if("-1".equals(upper))upper="*";return Multiplicity.builder().lower(lower).upper(upper).build();} String[] p=value.split("\\.\\.");return Multiplicity.builder().lower(p[0]).upper(p.length>1?"-1".equals(p[1])?"*":p[1]:p[0]).build(); }
    private String type(Element e, Map<String,String> extensionTypes, Map<String,String> namedTypes, List<String>w) { String t=extensionTypes.get(id(e)); if(t==null)t=e.getAttribute("type"); Element child=first(e,"type"); if(t.isBlank()&&child!=null){t=value(child,"name","");if(t.isBlank())t=externalReference(child);} if(t!=null&&t.contains("#"))t=t.substring(t.indexOf('#')+1); if(t!=null&&namedTypes.containsKey(t))t=namedTypes.get(t); if(t==null||t.isBlank()){w.add("Tipo no resuelto para " + value(e,"name","elemento"));return "Object";} return t; }
    private Visibility visibility(String v) { try{return Visibility.valueOf(v.toUpperCase(Locale.ROOT));}catch(Exception e){return Visibility.PACKAGE;} }
    private String value(Element e,String attr,String fallback){if(e==null)return fallback;String v=e.getAttribute(attr);return v.isBlank()?fallback:v;}
    private String id(Element e){String v=e.getAttributeNS("http://www.omg.org/XMI","id");if(v.isBlank())v=e.getAttribute("xmi:id");return v.isBlank()?null:v;}
    private String local(Node n){String l=n.getLocalName();return l==null?n.getNodeName().replaceFirst("^.*:",""):l;}
    private List<Element> elements(Document d,String n){List<Element> r=new ArrayList<>();NodeList l=d.getElementsByTagNameNS("*",n);if(l.getLength()==0)l=d.getElementsByTagName(n);for(int i=0;i<l.getLength();i++)r.add((Element)l.item(i));return r;}
    private List<Element> typedElements(Document d,String type){List<Element> r=new ArrayList<>();for(Element e:elements(d,"*")){String value=e.getAttribute("xmi:type");if(value.isBlank())value=e.getAttributeNS("http://www.omg.org/XMI","type");if((local(e).equals(type)||value.equals(type)||value.endsWith(":"+type))&&isInsideModel(e))r.add(e);}return r.stream().distinct().toList();}
    private List<Element> children(Element e,String... names){List<Element>r=new ArrayList<>();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element x&&Arrays.stream(names).anyMatch(s->s.equals(local(x))))r.add(x);return r;}
    private Element first(Element e,String n){for(Element x:children(e,n))return x;return null;}
    private String emptyToNull(String s){return s==null||s.isBlank()?null:s;}
    private boolean isInsideModel(Element element) { for(Node n=element.getParentNode();n instanceof Element parent;n=parent.getParentNode()) if("Model".equals(local(parent)))return true; return false; }
    private List<Element> classElements(Document d) { List<Element> result = new ArrayList<>(typedElements(d, "Class")); result.addAll(typedElements(d, "AssociationClass")); return result.stream().distinct().toList(); }
    private String externalReference(Element e) { if(e==null)return ""; String v=e.getAttribute("xmi:idref");if(v.isBlank())v=e.getAttribute("xmi:id");if(v.isBlank())v=e.getAttributeNS("http://schema.omg.org/spec/XMI/2.1","idref");return v; }
    private Map<String,String> namedTypes(Document d) { Map<String,String> result=new HashMap<>();for(Element e:typedElements(d,"PrimitiveType"))result.put(id(e),value(e,"name","Object"));for(Element e:typedElements(d,"DataType"))result.put(id(e),value(e,"name","Object"));return result; }
    private Map<String,String> extensionAttributeTypes(Document d) { Map<String,String> result=new HashMap<>();for(Element e:elements(d,"attribute")){String ref=externalReference(e);Element properties=first(e,"properties");String type=properties==null?"":properties.getAttribute("type");if(!ref.isBlank()&&!type.isBlank())result.put(ref,type);}return result; }
    private String[] connectorEndpoints(Document d,String relationId) { if(relationId==null)return null;for(Element connector:elements(d,"connector")){if(!relationId.equals(externalReference(connector)))continue;Element source=first(connector,"source"),target=first(connector,"target");if(source!=null&&target!=null)return new String[]{externalReference(source),externalReference(target)};}return null; }
    private boolean isAssociationClassConnector(Document d, String associationId) { for (Element connector : elements(d, "connector")) if (associationId != null && associationId.equals(externalReference(connector)) && associationClassId(connector) != null) return true; return false; }
    private String associationClassId(Element connector) { Element extended = first(connector, "extendedProperties"); if (extended == null) return null; String value = extended.getAttribute("associationclass"); return value.isBlank() ? null : value; }
    private Multiplicity connectorMultiplicity(Element end) { Element type = first(end, "type"); String value = type == null ? "" : type.getAttribute("multiplicity"); if (value.isBlank()) return Multiplicity.builder().lower("1").upper("1").build(); String[] parts = value.replace(" ", "").split("\\.\\.", -1); return Multiplicity.builder().lower(parts[0]).upper(parts.length > 1 ? "-1".equals(parts[1]) ? "*" : parts[1] : parts[0]).build(); }
    private Element endForClass(List<Element> ends,String externalClassId) { if(externalClassId==null)return null;for(Element end:ends){Element type=first(end,"type");if(externalClassId.equals(externalReference(type)))return end;}return null; }
}
