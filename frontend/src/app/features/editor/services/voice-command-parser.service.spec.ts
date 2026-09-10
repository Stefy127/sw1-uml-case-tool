import { VoiceCommandParserService } from './voice-command-parser.service';

describe('VoiceCommandParserService', () => {
  const parser = new VoiceCommandParserService();

  it.each([
    ['crear clase Cliente', 'CREATE_CLASS'],
    ['borra clase Cliente', 'DELETE_CLASS'],
    ['renombrar clase Cliente a Usuario', 'RENAME_CLASS'],
    ['añadir atributo email String a Cliente', 'ADD_ATTRIBUTE'],
    ['elimina atributo email de Cliente', 'REMOVE_ATTRIBUTE'],
    ['agregar método buscarCliente a Cliente', 'ADD_METHOD'],
    ['crear asociación entre Cliente y Pedido', 'CREATE_RELATION'],
    ['crear agregación entre Pedido y Producto', 'CREATE_RELATION'],
    ['crear composición entre Pedido y Detalle', 'CREATE_RELATION'],
    ['crear herencia de Empleado a Persona', 'CREATE_RELATION'],
    ['crear dependencia de Pedido a ServicioPago', 'CREATE_RELATION'],
  ])('interprets %s as %s', (text, type) => {
    const result = parser.parse(text);
    expect(result.success).toBe(true);
    expect(result.command?.type).toBe(type);
  });

  it('normalizes case and preserves class name', () => {
    const result = parser.parse('  CREAR   CLASE   MiCliente  ');
    expect(result.command?.className).toBe('MiCliente');
  });

  it('understands natural association wording and removes only spoken articles', () => {
    const result = parser.parse('Haz que el Cliente se asocie con Agua');
    expect(result.success).toBe(true);
    expect(result.command?.className).toBe('Cliente');
    expect(result.command?.secondaryClassName).toBe('Agua');
    expect(result.command?.relationType).toBe('ASSOCIATION');
  });

  it.each([
    ['crear clase', 'Falta el nombre de la clase.'],
    ['agregar atributo email', 'Faltan el tipo y la clase del atributo.'],
    ['crear asociación Cliente', 'Falta la segunda clase de la asociación.'],
    ['haz algo bonito con Cliente', 'No pude interpretar ese comando.'],
  ])('reports invalid command %s', (text, error) => {
    const result = parser.parse(text);
    expect(result.success).toBe(false);
    expect(result.errors).toContain(error);
  });
});
