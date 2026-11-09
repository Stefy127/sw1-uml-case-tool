import { DiagramViewState, UmlAttribute, UmlClass, UmlDiagram, UmlMethod, UmlRelation } from '../../editor/models/diagram.model';

export interface TemplateAttributeSpec { name: string; type: string; primaryKey?: boolean; }
export interface TemplateMethodSpec { name: string; returnType: string; }
export interface TemplateClassSpec { name: string; attributes: TemplateAttributeSpec[]; methods?: TemplateMethodSpec[]; }
export interface TemplateRelationSpec {
  source: string; target: string; type: string;
  sourceMultiplicity: { lower: string; upper: string };
  targetMultiplicity: { lower: string; upper: string };
  sourceRole?: string; targetRole?: string;
}
export interface TemplateDefinition {
  name: string; description: string; icon: string; color: string; classes: string[]; relations: number;
  classSpecs: TemplateClassSpec[]; relationSpecs: TemplateRelationSpec[];
}
export interface TemplateDiagramPayload { canonicalModel: UmlDiagram; viewState: DiagramViewState; }

export function instantiateTemplate(template: TemplateDefinition, diagramName: string): TemplateDiagramPayload {
  const classIds = new Map<string, string>();
  const newId = () => crypto.randomUUID();
  const classes: UmlClass[] = template.classSpecs.map((spec) => {
    const id = newId();
    classIds.set(spec.name, id);
    const attributes: UmlAttribute[] = spec.attributes.map((attribute) => ({
      id: newId(), name: attribute.name, type: attribute.type, visibility: 'PUBLIC', isStatic: false,
      isFinal: false, defaultValue: null, primaryKey: attribute.primaryKey ?? false,
    }));
    const methods: UmlMethod[] = (spec.methods ?? []).map((method) => ({
      id: newId(), name: method.name, returnType: method.returnType, visibility: 'PUBLIC', isStatic: false, parameters: [],
    }));
    return { id, name: spec.name, isAbstract: false, attributes, methods };
  });
  const relations: UmlRelation[] = template.relationSpecs.map((spec) => ({
    id: newId(), sourceClassId: classIds.get(spec.source)!, targetClassId: classIds.get(spec.target)!, type: spec.type,
    sourceMultiplicity: spec.sourceMultiplicity, targetMultiplicity: spec.targetMultiplicity,
    sourceRole: spec.sourceRole ?? null, targetRole: spec.targetRole ?? null, sourceNavigable: true, targetNavigable: true,
  }));
  const canonicalModel: UmlDiagram = { id: '', name: diagramName, version: 0, classes, relations };
  const viewState: DiagramViewState = {
    diagramId: '',
    nodes: classes.map((umlClass, index) => ({ classId: umlClass.id, x: 80 + (index % 3) * 330, y: 70 + Math.floor(index / 3) * 280, width: 240, height: 180 })),
    relations: relations.map((relation) => ({ relationId: relation.id })),
  };
  return { canonicalModel, viewState };
}
