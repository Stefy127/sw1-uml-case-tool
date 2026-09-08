import { Injectable, signal } from '@angular/core';

export interface ThemePreset {
  name: string;
  colors: Record<string, string>;
}

const base = {
  primary: '#7564dc',
  secondary: '#8b9bef',
  background: '#fbfaff',
  surface: '#ffffff',
  canvas: '#fcfbff',
  border: '#e9e7f1',
  classHeader: '#eeeaff',
};

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly presets: ThemePreset[] = [
    { name: 'Lavender', colors: base },
    {
      name: 'Rose',
      colors: {
        ...base,
        primary: '#d96d9d',
        secondary: '#e9a6bf',
        background: '#fff9fc',
        surface: '#fff',
        canvas: '#fffafe',
        border: '#f0dce6',
        classHeader: '#fde7f0',
      },
    },
    {
      name: 'Sky',
      colors: {
        ...base,
        primary: '#579bc4',
        secondary: '#8fcbe2',
        background: '#f7fcff',
        surface: '#fff',
        canvas: '#f9fdff',
        border: '#dbeaf1',
        classHeader: '#e3f3fb',
      },
    },
    {
      name: 'Mint',
      colors: {
        ...base,
        primary: '#4ca98f',
        secondary: '#87cfba',
        background: '#f7fdfa',
        surface: '#fff',
        canvas: '#fafffd',
        border: '#d8eee7',
        classHeader: '#e2f6ee',
      },
    },
    {
      name: 'Peach',
      colors: {
        ...base,
        primary: '#d98d69',
        secondary: '#f0b79a',
        background: '#fffaf7',
        surface: '#fff',
        canvas: '#fffdfb',
        border: '#f1e0d7',
        classHeader: '#ffeadf',
      },
    },
    {
      name: 'Lilac',
      colors: {
        ...base,
        primary: '#9273c8',
        secondary: '#b89adf',
        background: '#fbf9ff',
        surface: '#fff',
        canvas: '#fdfbff',
        border: '#e9def5',
        classHeader: '#f0e6fb',
      },
    },
    {
      name: 'Butter',
      colors: {
        ...base,
        primary: '#c39a43',
        secondary: '#e2c774',
        background: '#fffdf6',
        surface: '#fff',
        canvas: '#fffef9',
        border: '#eee5c9',
        classHeader: '#fff5cd',
      },
    },
    {
      name: 'Sage',
      colors: {
        ...base,
        primary: '#789870',
        secondary: '#a9c49e',
        background: '#fafcf8',
        surface: '#fff',
        canvas: '#fcfefa',
        border: '#dfe9dc',
        classHeader: '#eaf3e5',
      },
    },
    {
      name: 'Neutral',
      colors: {
        ...base,
        primary: '#667085',
        secondary: '#9aa5b5',
        background: '#fafafa',
        surface: '#fff',
        canvas: '#fcfcfc',
        border: '#e5e7eb',
        classHeader: '#f0f1f3',
      },
    },
  ];
  readonly active = signal('Lavender');
  readonly custom = signal<Record<string, string>>({ ...base });

  constructor() {
    this.load();
  }
  select(preset: ThemePreset) {
    this.active.set(preset.name);
    this.custom.set({ ...preset.colors });
    this.apply(preset.colors);
    this.save();
  }
  setCustomColor(key: string, value: string) {
    const colors = { ...this.custom(), [key]: value };
    this.custom.set(colors);
    this.apply(colors);
    localStorage.setItem('sw1-theme-colors', JSON.stringify(colors));
  }
  restore() {
    this.select(this.presets[0]);
  }
  applyServerTheme(themeName: string | null | undefined) {
    const preset = this.presets.find((candidate) => candidate.name.toUpperCase() === (themeName ?? 'LAVENDER').toUpperCase()) ?? this.presets[0];
    this.active.set(preset.name);
    this.custom.set({ ...preset.colors });
    this.apply(preset.colors);
    localStorage.setItem('sw1-theme', preset.name);
    localStorage.setItem('sw1-theme-colors', JSON.stringify(preset.colors));
  }
  private apply(colors: Record<string, string>) {
    Object.entries(colors).forEach(([key, value]) =>
      document.documentElement.style.setProperty(
        `--color-${key.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())}`,
        value,
      ),
    );
  }
  private save() {
    localStorage.setItem('sw1-theme', this.active());
    localStorage.setItem('sw1-theme-colors', JSON.stringify(this.custom()));
  }
  private load() {
    const saved = localStorage.getItem('sw1-theme-colors');
    if (saved) {
      const colors = JSON.parse(saved) as Record<string, string>;
      this.custom.set(colors);
      this.apply(colors);
      this.active.set(localStorage.getItem('sw1-theme') ?? 'Lavender');
    }
  }
}
