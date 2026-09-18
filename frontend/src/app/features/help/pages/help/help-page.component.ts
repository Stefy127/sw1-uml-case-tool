import { Component } from '@angular/core';

@Component({
  selector: 'app-help-page',
  templateUrl: './help-page.component.html',
  styleUrl: './help-page.component.scss',
})
export class HelpPageComponent {
  readonly topics = [
    ['Crear una clase', 'Usa la herramienta de clase y escribe un nombre. Puedes mover y redimensionar la clase directamente en el lienzo.'],
    ['Agregar atributos', 'Añade atributos desde Propiedades, con el botón + de la clase o haciendo doble clic en su zona de atributos.'],
    ['Crear relaciones', 'Activa Relación, selecciona el origen y después el destino. Elige tipo, roles y multiplicidades desde Propiedades.'],
    ['Multiplicidades', '1 significa exactamente uno; 0..1 indica una relación opcional; 0..* permite una colección opcional; 1..* exige al menos un elemento.'],
    ['Agregación y composición', 'Usa agregación para una relación parte-todo independiente y composición cuando la vida de la parte depende del todo.'],
    ['Herencia y dependencia', 'Herencia expresa especialización. Dependencia indica que una clase utiliza a otra sin crear una clave foránea.'],
    ['Importar y exportar XMI', 'Encuentra las acciones en las herramientas del editor para llevar tu modelo a otro sistema compatible.'],
    ['Generar backend', 'Desde el editor puedes generar un proyecto Spring Boot a partir del modelo UML validado.'],
    ['Colaboración y voz/IA', 'Las herramientas de colaboración y entrada por voz están disponibles desde el editor cuando el proyecto lo permite.'],
    ['Atajos del editor', 'Enter confirma nombres y atributos; Escape cancela la edición. Doble clic permite editar nombres y atributos rápidamente.'],
  ];
}
