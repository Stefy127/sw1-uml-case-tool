import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

@Injectable({ providedIn: 'root' })
export class VoiceRecognitionService {
  private recognition: SpeechRecognitionLike | null = null;

  isSupported(): boolean {
    return this.constructorForBrowser() !== null;
  }

  listen(): Observable<{ type: 'result' | 'error' | 'end'; text?: string; message?: string }> {
    return new Observable((subscriber) => {
      const Recognition = this.constructorForBrowser();
      if (!Recognition) { subscriber.next({ type: 'error', message: 'El reconocimiento de voz no está disponible en este navegador.' }); subscriber.complete(); return; }
      const recognition = new Recognition();
      this.recognition = recognition;
      recognition.lang = 'es-BO';
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.onresult = (event) => subscriber.next({ type: 'result', text: event.results[0][0].transcript });
      recognition.onerror = (event) => subscriber.next({ type: 'error', message: this.errorMessage(event.error) });
      recognition.onend = () => { subscriber.next({ type: 'end' }); subscriber.complete(); this.recognition = null; };
      try { recognition.start(); } catch { subscriber.next({ type: 'error', message: 'No se pudo iniciar el micrófono.' }); subscriber.complete(); }
      return () => this.stop();
    });
  }

  stop(): void { this.recognition?.stop(); this.recognition = null; }

  private constructorForBrowser(): SpeechRecognitionConstructor | null {
    const scope = globalThis as unknown as { SpeechRecognition?: SpeechRecognitionConstructor; webkitSpeechRecognition?: SpeechRecognitionConstructor };
    return scope.SpeechRecognition ?? scope.webkitSpeechRecognition ?? null;
  }

  private errorMessage(error: string): string {
    return error === 'not-allowed' || error === 'service-not-allowed' ? 'No se concedió permiso para usar el micrófono.' : error === 'no-speech' ? 'No se detectó voz.' : error === 'audio-capture' ? 'No se encontró un micrófono disponible.' : 'No se pudo reconocer el comando de voz.';
  }
}
