import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ProjectService } from '../../../projects/services/project.service';
import { AuthService } from '../../../auth/services/auth.service';
import { DiagramService } from '../../../editor/services/diagram.service';
import { instantiateTemplate, TemplateDefinition } from '../../models/template.model';

@Component({
  selector: 'app-templates-page',
  templateUrl: './templates-page.component.html',
  styleUrl: './templates-page.component.scss',
})
export class TemplatesPageComponent {
  private readonly projects = inject(ProjectService);
  private readonly diagrams = inject(DiagramService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly creating = signal('');
  readonly error = signal('');
  readonly templates: TemplateDefinition[] = [
    {
      name: 'Biblioteca', description: 'Catálogo, préstamos y lectores para una biblioteca.', icon: '▤', color: 'lavender', classes: ['Libro', 'Usuario', 'Préstamo'], relations: 3,
      classSpecs: [
        { name: 'Libro', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'titulo', type: 'String' }, { name: 'isbn', type: 'String' }], methods: [{ name: 'prestar', returnType: 'void' }] },
        { name: 'Usuario', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'email', type: 'String' }], methods: [{ name: 'solicitarPrestamo', returnType: 'void' }] },
        { name: 'Préstamo', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'fechaInicio', type: 'Date' }, { name: 'fechaDevolucion', type: 'Date' }], methods: [{ name: 'registrarDevolucion', returnType: 'void' }] },
      ],
      relationSpecs: [
        { source: 'Usuario', target: 'Préstamo', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' }, sourceRole: 'usuario', targetRole: 'préstamos' },
        { source: 'Libro', target: 'Préstamo', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' }, sourceRole: 'libro', targetRole: 'préstamos' },
        { source: 'Usuario', target: 'Libro', type: 'DEPENDENCY', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' } },
      ],
    },
    {
      name: 'Tienda / E-commerce', description: 'Productos, pedidos y clientes para una tienda digital.', icon: '◇', color: 'peach', classes: ['Producto', 'Cliente', 'Pedido'], relations: 4,
      classSpecs: [
        { name: 'Producto', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'precio', type: 'Decimal' }], methods: [{ name: 'actualizarStock', returnType: 'void' }] },
        { name: 'Cliente', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'email', type: 'String' }], methods: [{ name: 'crearPedido', returnType: 'Pedido' }] },
        { name: 'Pedido', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'fecha', type: 'Date' }, { name: 'estado', type: 'String' }], methods: [{ name: 'confirmar', returnType: 'void' }, { name: 'cancelar', returnType: 'void' }] },
      ],
      relationSpecs: [
        { source: 'Cliente', target: 'Pedido', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' }, sourceRole: 'cliente', targetRole: 'pedidos' },
        { source: 'Pedido', target: 'Producto', type: 'ASSOCIATION', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' }, sourceRole: 'pedidos', targetRole: 'productos' },
        { source: 'Cliente', target: 'Producto', type: 'DEPENDENCY', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Producto', target: 'Pedido', type: 'DEPENDENCY', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' } },
      ],
    },
    {
      name: 'Clínica', description: 'Pacientes, citas y profesionales de la salud.', icon: '✚', color: 'mint', classes: ['Paciente', 'Cita', 'Médico'], relations: 3,
      classSpecs: [
        { name: 'Paciente', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'fechaNacimiento', type: 'Date' }], methods: [{ name: 'agendarCita', returnType: 'void' }] },
        { name: 'Cita', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'fecha', type: 'Date' }, { name: 'motivo', type: 'String' }], methods: [{ name: 'confirmar', returnType: 'void' }] },
        { name: 'Médico', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'especialidad', type: 'String' }], methods: [{ name: 'atender', returnType: 'void' }] },
      ],
      relationSpecs: [
        { source: 'Paciente', target: 'Cita', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Médico', target: 'Cita', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Paciente', target: 'Médico', type: 'DEPENDENCY', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' } },
      ],
    },
    {
      name: 'Universidad', description: 'Alumnos, cursos y matrículas para un campus.', icon: '⌂', color: 'sky', classes: ['Alumno', 'Curso', 'Matrícula'], relations: 4,
      classSpecs: [
        { name: 'Alumno', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'email', type: 'String' }], methods: [{ name: 'inscribirse', returnType: 'void' }] },
        { name: 'Curso', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'creditos', type: 'Integer' }], methods: [{ name: 'asignarDocente', returnType: 'void' }] },
        { name: 'Matrícula', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'fecha', type: 'Date' }, { name: 'estado', type: 'String' }], methods: [{ name: 'confirmar', returnType: 'void' }] },
      ],
      relationSpecs: [
        { source: 'Alumno', target: 'Matrícula', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Curso', target: 'Matrícula', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Alumno', target: 'Curso', type: 'ASSOCIATION', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' } },
        { source: 'Curso', target: 'Alumno', type: 'DEPENDENCY', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '0', upper: '*' } },
      ],
    },
    {
      name: 'Sistema de ventas básico', description: 'Clientes, productos, ventas, detalles y pagos para gestionar ventas.', icon: '▦', color: 'lavender', classes: ['Cliente', 'Producto', 'Venta', 'DetalleVenta', 'Pago'], relations: 4,
      classSpecs: [
        { name: 'Cliente', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'email', type: 'String' }], methods: [{ name: 'registrarVenta', returnType: 'void' }] },
        { name: 'Producto', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'nombre', type: 'String' }, { name: 'precio', type: 'Decimal' }, { name: 'stock', type: 'Integer' }], methods: [{ name: 'actualizarStock', returnType: 'void' }] },
        { name: 'Venta', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'fecha', type: 'Date' }, { name: 'total', type: 'Decimal' }, { name: 'estado', type: 'String' }], methods: [{ name: 'calcularTotal', returnType: 'Decimal' }, { name: 'confirmar', returnType: 'void' }] },
        { name: 'DetalleVenta', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'cantidad', type: 'Integer' }, { name: 'precioUnitario', type: 'Decimal' }, { name: 'subtotal', type: 'Decimal' }], methods: [{ name: 'calcularSubtotal', returnType: 'Decimal' }] },
        { name: 'Pago', attributes: [{ name: 'id', type: 'Long', primaryKey: true }, { name: 'fecha', type: 'Date' }, { name: 'monto', type: 'Decimal' }, { name: 'metodo', type: 'String' }], methods: [{ name: 'registrar', returnType: 'void' }] },
      ],
      relationSpecs: [
        { source: 'Cliente', target: 'Venta', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' }, sourceRole: 'cliente', targetRole: 'ventas' },
        { source: 'Venta', target: 'DetalleVenta', type: 'COMPOSITION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '1', upper: '*' }, sourceRole: 'venta', targetRole: 'detalles' },
        { source: 'DetalleVenta', target: 'Producto', type: 'ASSOCIATION', sourceMultiplicity: { lower: '0', upper: '*' }, targetMultiplicity: { lower: '1', upper: '1' }, sourceRole: 'detalles', targetRole: 'producto' },
        { source: 'Venta', target: 'Pago', type: 'ASSOCIATION', sourceMultiplicity: { lower: '1', upper: '1' }, targetMultiplicity: { lower: '0', upper: '*' }, sourceRole: 'venta', targetRole: 'pagos' },
      ],
    },
  ];

  useTemplate(template: TemplateDefinition): void {
    const ownerUserId = this.auth.currentUser()?.id;
    if (!ownerUserId) { this.error.set('Inicia sesión para usar una plantilla.'); return; }
    this.creating.set(template.name);
    this.error.set('');
    const diagramName = `${template.name} · Modelo UML`;
    const payload = instantiateTemplate(template, diagramName);
    this.projects.createProject({ name: template.name, description: template.description, ownerUserId, diagramName, canonicalModel: payload.canonicalModel, viewState: payload.viewState }).subscribe({
      next: project => {
        this.diagrams.getDiagramsByProject(project.id).subscribe({
          next: diagrams => {
            const diagram = diagrams[0];
            this.creating.set('');
            if (diagram) void this.router.navigate(['/editor', diagram.id]);
            else this.error.set('No se pudo localizar el diagrama creado.');
          },
          error: () => { this.creating.set(''); this.error.set('No se pudo cargar el diagrama creado.'); },
        });
      },
      error: () => { this.creating.set(''); this.error.set('No se pudo crear el proyecto.'); },
    });
  }
}
