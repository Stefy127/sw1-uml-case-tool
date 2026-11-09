import { instantiateTemplate, TemplateDefinition } from './template.model';

describe('template model', () => {
  it('creates a complete sales model with unique ids and visible nodes', () => {
    const template: TemplateDefinition = {
      name: 'Sistema de ventas básico', description: '', icon: '▦', color: 'lavender',
      classes: ['Cliente', 'Producto', 'Venta', 'DetalleVenta', 'Pago'], relations: 4,
      classSpecs: [
        { name: 'Cliente', attributes: [{ name: 'id', type: 'Long', primaryKey: true }] },
        { name: 'Producto', attributes: [{ name: 'id', type: 'Long', primaryKey: true }] },
        { name: 'Venta', attributes: [{ name: 'id', type: 'Long', primaryKey: true }] },
        { name: 'DetalleVenta', attributes: [{ name: 'id', type: 'Long', primaryKey: true }] },
        { name: 'Pago', attributes: [{ name: 'id', type: 'Long', primaryKey: true }] },
      ],
      relationSpecs: [
        { source: 'Cliente', target: 'Venta', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Venta', target: 'DetalleVenta', type: 'COMPOSITION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '1', upper: '*' } },
        { source: 'DetalleVenta', target: 'Producto', type: 'ASSOCIATION', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '1', upper: '1' } },
        { source: 'Venta', target: 'Pago', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' } },
      ],
    };

    const payload = instantiateTemplate(template, 'Ventas · Modelo UML');
    const ids = [
      ...payload.canonicalModel.classes.map((item) => item.id),
      ...payload.canonicalModel.classes.flatMap((item) => item.attributes.map((attribute) => attribute.id)),
      ...payload.canonicalModel.relations.map((item) => item.id),
    ];

    expect(payload.canonicalModel.classes).toHaveLength(5);
    expect(payload.canonicalModel.relations).toHaveLength(4);
    expect(new Set(ids).size).toBe(ids.length);
    expect(payload.viewState.nodes).toHaveLength(5);
    expect(payload.viewState.nodes.map((node) => node.classId)).toEqual(payload.canonicalModel.classes.map((item) => item.id));
    expect(payload.viewState.relations.map((view) => view.relationId)).toEqual(payload.canonicalModel.relations.map((item) => item.id));
  });
});
