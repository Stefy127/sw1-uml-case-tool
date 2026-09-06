import { Injectable } from '@angular/core';
import { VoiceCommand, VoiceCommandResult } from '../models/voice-command.model';

@Injectable({ providedIn: 'root' })
export class VoiceCommandParserService {
  parse(text: string): VoiceCommandResult {
    const original = text.trim();
    const normalized = this.normalize(original);
    if (!normalized) return this.failure('Escribe o dicta un comando.');

    let match = normalized.match(/^(?:crear|crea) (?:una )?clase(?: llamada)?(?:\s+)(.+)$/);
    if (match) return this.success({ type: 'CREATE_CLASS', className: this.restoreName(match[1], original, /clase(?: llamada)?\s+(.+)$/i) });
    if (/^(?:crear|crea) (?:una )?clase$/.test(normalized)) return this.failure('Falta el nombre de la clase.');

    match = normalized.match(/^(?:eliminar|elimina|borrar|borra) (?:la )?clase\s+(.+)$/);
    if (match) return this.success({ type: 'DELETE_CLASS', className: match[1].trim() });

    match = normalized.match(/^renombrar clase\s+(.+?)\s+a\s+(.+)$/);
    if (match) return this.success({ type: 'RENAME_CLASS', className: match[1].trim(), newClassName: match[2].trim() });

    match = normalized.match(/^(?:agregar|agrega|añadir|anadir) atributo\s+(\S+)\s+(\S+)\s+a\s+(.+)$/);
    if (match) return this.success({ type: 'ADD_ATTRIBUTE', attributeName: match[1], attributeType: this.typeName(match[2]), className: match[3].trim() });
    if (/^(?:agregar|agrega|añadir|anadir) atributo\s+\S+$/.test(normalized)) return this.failure('Faltan el tipo y la clase del atributo.');

    match = normalized.match(/^(?:eliminar|elimina|borrar|borra) atributo\s+(.+?)\s+de\s+(.+)$/);
    if (match) return this.success({ type: 'REMOVE_ATTRIBUTE', attributeName: match[1].trim(), className: match[2].trim() });

    match = normalized.match(/^agregar metodo\s+(\S+)(?:\s+a\s+(.+))?$/);
    if (match) return this.success({ type: 'ADD_METHOD', methodName: match[1], className: match[2]?.trim() });

    const relation = normalized.match(/^(?:crear|crea|asociar)\s+(asociacion|agregacion|composicion|herencia|dependencia)(?:\s+entre\s+|\s+de\s+)(.+?)(?:\s+y\s+|\s+a\s+)(.+)$/);
    if (relation) {
      const relationType = this.relationType(relation[1]);
      return relationType
        ? this.success({ type: 'CREATE_RELATION', relationType, className: relation[2].trim(), secondaryClassName: relation[3].trim() })
        : this.failure('Tipo de relación no reconocido.');
    }
    if (/^crear asociacion\s+\S+$/.test(normalized)) return this.failure('Falta la segunda clase de la asociación.');
    return this.failure('No pude interpretar ese comando.');
  }

  private normalize(text: string): string {
    return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[.,!?;:]/g, ' ').replace(/\s+/g, ' ').trim();
  }

  private restoreName(value: string, original: string, pattern: RegExp): string {
    return original.match(pattern)?.[1]?.trim() ?? value.trim();
  }

  private typeName(value: string): string {
    const types: Record<string, string> = { string: 'String', long: 'Long', integer: 'Integer', boolean: 'Boolean', date: 'Date', localdate: 'LocalDate', bigdecimal: 'BigDecimal', double: 'Double', float: 'Float', uuid: 'UUID' };
    return types[value] ?? value;
  }

  private relationType(value: string) {
    const normalized = this.normalize(value);
    return normalized === 'asociacion' ? 'ASSOCIATION' : normalized === 'agregacion' ? 'AGGREGATION' : normalized === 'composicion' ? 'COMPOSITION' : normalized === 'herencia' ? 'INHERITANCE' : normalized === 'dependencia' ? 'DEPENDENCY' : null;
  }

  private success(command: VoiceCommand): VoiceCommandResult { return { success: true, command, errors: [] }; }
  private failure(error: string): VoiceCommandResult { return { success: false, command: null, errors: [error] }; }
}
