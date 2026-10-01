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

  it.each(['Cliente', 'cliente', 'CLIENTE', 'Clíente', '  cliente  '])(
    'accepts a class command with the name variant %s',
    (className) => {
      const result = parser.parse(`elimina la clase ${className}`);
      expect(result.success).toBe(true);
      expect(result.command?.className).toBe('cliente');
    },
  );

  it('extracts both normalized class names in a relation command', () => {
    const result = parser.parse('crear asociación entre Clíente y Pedído');
    expect(result.success).toBe(true);
    expect(result.command?.className).toBe('cliente');
    expect(result.command?.secondaryClassName).toBe('pedido');
  });

  it('understands natural association wording and removes only spoken articles', () => {
    const result = parser.parse('Haz que el Cliente se asocie con Agua');
    expect(result.success).toBe(true);
    expect(result.command?.className).toBe('Cliente');
    expect(result.command?.secondaryClassName).toBe('Agua');
    expect(result.command?.relationType).toBe('ASSOCIATION');
  });

  it.each([
    ['crea el atributo b String en la clase cliente', 'ADD_ATTRIBUTE'],
    ['añade nombre de tipo String a la clase Clíente', 'ADD_ATTRIBUTE'],
    ['cambia el nombre del atributo nombre a nombreCompleto en cliente', 'RENAME_ATTRIBUTE'],
    ['cambia el tipo del atributo edad a Integer en CLIENTE', 'CHANGE_ATTRIBUTE_TYPE'],
    ['elimina el atributo nombre de Clíente', 'REMOVE_ATTRIBUTE'],
    ['renombra el método guardar a guardarCambios en cliente', 'RENAME_METHOD'],
    ['elimina el método guardar de CLIENTE', 'REMOVE_METHOD'],
    ['elimina la relación entre cliente y pedido', 'REMOVE_RELATION'],
    ['qué atributos tiene Clíente', 'READ_CLASS_ATTRIBUTES'],
    ['qué clases existen', 'LIST_CLASSES'],
  ])('supports flexible CRUD/read wording: %s', (text, type) => {
    const result = parser.parse(text);
    expect(result.success).toBe(true);
    expect(result.command?.type).toBe(type);
  });

  it('keeps normalized aliases for both ends of a relation removal', () => {
    const result = parser.parse('borra la asociación entre Clíente y Pedído');
    expect(result.command).toEqual({ type: 'REMOVE_RELATION', className: 'cliente', secondaryClassName: 'pedido' });
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
