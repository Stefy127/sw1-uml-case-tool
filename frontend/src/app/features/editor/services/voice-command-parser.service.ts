import { Injectable } from '@angular/core';
import { VoiceCommand, VoiceCommandResult } from '../models/voice-command.model';
import { normalizeClassName } from '../utils/class-name.util';

@Injectable({ providedIn: 'root' })
export class VoiceCommandParserService {
  parse(text: string): VoiceCommandResult {
    const original = text.trim();
    const normalized = this.normalize(original);
    if (!normalized) return this.failure('Escribe o dicta un comando.');
    let match = normalized.match(/^(?:crear|crea) (?:una )?clase(?: llamada)?\s+(.+)$/);
    if (match) return this.success({ type: 'CREATE_CLASS', className: this.restoreName(match[1], original, /clase(?: llamada)?\s+(.+)$/i) });
    if (/^(?:crear|crea) (?:una )?clase$/.test(normalized)) return this.failure('Falta el nombre de la clase.');
    match = normalized.match(/^(?:eliminar|elimina|borrar|borra) (?:la )?clase\s+(.+)$/);
    if (match) return this.success({ type: 'DELETE_CLASS', className: match[1].trim() });
    match = normalized.match(/^(?:renombrar|renombra|cambiar|cambia) (?:el nombre de )?(?:la )?clase\s+(.+?)\s+(?:a|por)\s+(.+)$/);
    if (match) return this.success({ type: 'RENAME_CLASS', className: match[1].trim(), newClassName: match[2].trim() });
    if (/^(?:que|cuales) clases (?:existen|hay)|^(?:lista|listar|mostrar|muestra) clases$/.test(normalized)) return this.success({ type: 'LIST_CLASSES' });
    match = normalized.match(/^(?:agregar|agrega|anadir|anade|crear|crea) (?:el )?metodo\s+(\S+)(?:\s+de tipo\s+(\S+))?(?:\s+a\s+(?:la clase\s+)?(.+))?$/);
    if (match) return this.success({ type: 'ADD_METHOD', methodName: match[1], returnType: this.typeName(match[2] ?? 'void'), className: match[3]?.trim() });
    match = normalized.match(/^(?:agregar|agrega|anadir|anade|crear|crea) (?:el )?(?:atributo|campo)\s+(\S+)(?:\s+de tipo)?\s+(\S+)\s+(?:a|en)\s+(?:la clase\s+)?(.+)$/);
    if (!match) match = normalized.match(/^(?:agregar|agrega|anadir|anade|crear|crea)\s+(\S+)\s+de tipo\s+(\S+)\s+(?:a|en)\s+(?:la clase\s+)?(.+)$/);
    if (!match) match = normalized.match(/^(?:agregar|agrega|anadir|anade|crear|crea)\s+(\S+)\s+(\S+)\s+(?:a|en)\s+(?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'ADD_ATTRIBUTE', attributeName: match[1], attributeType: this.typeName(match[2]), className: match[3].trim() });
    if (/^(?:agregar|agrega|anadir|anade|crear|crea) (?:el )?(?:atributo|campo)\s+\S+$/.test(normalized)) return this.failure('Faltan el tipo y la clase del atributo.');
    match = normalized.match(/^(?:renombrar|renombra|cambiar|cambia) (?:el nombre del )?atributo\s+(\S+)\s+(?:a|por)\s+(\S+)\s+(?:en|de)\s+(?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'RENAME_ATTRIBUTE', attributeName: match[1], newAttributeName: match[2], className: match[3].trim() });
    match = normalized.match(/^(?:cambiar|cambia) (?:el )?tipo (?:del atributo\s+)?(\S+)\s+(?:a|por)\s+(\S+)\s+(?:en|de)\s+(?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'CHANGE_ATTRIBUTE_TYPE', attributeName: match[1], attributeType: this.typeName(match[2]), className: match[3].trim() });
    match = normalized.match(/^(?:eliminar|elimina|borrar|borra|quitar|quita) (?:el )?(?:atributo|campo)\s+(.+?)\s+(?:de|en)\s+(?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'REMOVE_ATTRIBUTE', attributeName: match[1].trim(), className: match[2].trim() });
    match = normalized.match(/^(?:que|cuales) atributos tiene (?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'READ_CLASS_ATTRIBUTES', className: match[1].trim() });
    match = normalized.match(/^(?:renombrar|renombra|cambiar|cambia) (?:el nombre del )?(?:el )?metodo\s+(\S+)\s+(?:a|por)\s+(\S+)\s+(?:en|de)\s+(?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'RENAME_METHOD', methodName: match[1], newMethodName: match[2], className: match[3].trim() });
    match = normalized.match(/^(?:eliminar|elimina|borrar|borra|quitar|quita) (?:el )?metodo\s+(\S+)\s+(?:de|en)\s+(?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'REMOVE_METHOD', methodName: match[1], className: match[2].trim() });
    match = normalized.match(/^(?:que|cuales) metodos tiene (?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'READ_CLASS_METHODS', className: match[1].trim() });
    match = normalized.match(/^(?:eliminar|elimina|borrar|borra|quitar|quita) (?:la )?(?:relacion|asociacion|agregacion|composicion) (?:entre|de)\s+(.+?)\s+(?:y|con)\s+(.+)$/);
    if (match) return this.success({ type: 'REMOVE_RELATION', className: this.cleanClassName(match[1]), secondaryClassName: this.cleanClassName(match[2]) });
    match = normalized.match(/^(?:que|cuales) relaciones tiene (?:la clase\s+)?(.+)$/);
    if (match) return this.success({ type: 'READ_CLASS_RELATIONS', className: match[1].trim() });
    const naturalAssociation = normalized.match(/^haz que (?:el |la )?(.+?) se asocie con (?:el |la )?(.+)$/);
    if (naturalAssociation) {
      const originalAssociation = original.match(/^haz que (?:el |la )?(.+?) se asocie con (?:el |la )?(.+)$/i);
      return this.success({ type: 'CREATE_RELATION', relationType: 'ASSOCIATION', className: this.cleanClassName(originalAssociation?.[1] ?? naturalAssociation[1]), secondaryClassName: this.cleanClassName(originalAssociation?.[2] ?? naturalAssociation[2]) });
    }
    const simpleAssociation = normalized.match(/^(?:relaciona|relacionar)\s+(?:a\s+)?(?:el |la )?(.+?)\s+con\s+(?:el |la )?(.+)$/);
    if (simpleAssociation) return this.success({ type: 'CREATE_RELATION', relationType: 'ASSOCIATION', className: this.cleanClassName(simpleAssociation[1]), secondaryClassName: this.cleanClassName(simpleAssociation[2]) });
    const relation = normalized.match(/^(?:crear|crea|asociar)\s+(asociacion|agregacion|composicion|herencia|dependencia)(?:\s+entre\s+|\s+de\s+)(.+?)(?:\s+y\s+|\s+a\s+)(.+)$/);
    if (relation) return this.success({ type: 'CREATE_RELATION', relationType: this.relationType(relation[1])!, className: relation[2].trim(), secondaryClassName: relation[3].trim() });
    if (/^crear asociacion\s+\S+$/.test(normalized)) return this.failure('Falta la segunda clase de la asociación.');
    return this.failure('No pude interpretar ese comando.');
  }
  private normalize(text: string): string { return normalizeClassName(text).replace(/[.,!?;:]/g, ' ').replace(/\s+/g, ' ').trim(); }
  private restoreName(value: string, original: string, pattern: RegExp): string { return original.match(pattern)?.[1]?.trim() ?? value.trim(); }
  private typeName(value: string): string { const types: Record<string, string> = { string: 'String', long: 'Long', integer: 'Integer', boolean: 'Boolean', date: 'Date', localdate: 'LocalDate', bigdecimal: 'BigDecimal', double: 'Double', float: 'Float', uuid: 'UUID', void: 'void' }; return types[value] ?? value; }
  private cleanClassName(value: string): string { return value.trim().replace(/^(?:el|la|los|las)\s+/i, ''); }
  private relationType(value: string) { const normalized = this.normalize(value); return normalized === 'asociacion' ? 'ASSOCIATION' : normalized === 'agregacion' ? 'AGGREGATION' : normalized === 'composicion' ? 'COMPOSITION' : normalized === 'herencia' ? 'INHERITANCE' : normalized === 'dependencia' ? 'DEPENDENCY' : null; }
  private success(command: VoiceCommand): VoiceCommandResult { return { success: true, command, errors: [] }; }
  private failure(error: string): VoiceCommandResult { return { success: false, command: null, errors: [error] }; }
}
