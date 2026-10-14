# Contexto del proyecto Senda

Equipo **Fuerza Salvaje** (Tecsup). Este documento transcribe y ordena la base oficial del proyecto.

**Fuentes y prioridad**

1. `docs/base/*.png` (9 imágenes): fuente oficial, manda sobre todo lo demás.
2. Decisiones técnicas tomadas por el equipo (ver `CLAUDE.md`).
3. `docs/ideas_extra/*.png`: ideas de un compañero, prioridad secundaria. Nunca reemplazan ni contradicen la base.

Los vacíos y contradicciones de la base (sección 8) quedaron resueltos por el Product Owner (sección 9). Las secciones 1 a 5 transcriben la base tal cual; la sección 6 es la versión final de las reglas.

---

## 1. Problema: "Semana de exámenes"

Fuente: `01_problema.png`.

### 1.1 Preguntas de la entrevista

1. ¿Cómo decidiste qué cursos estudiar primero?
2. ¿Cómo te organizaste la última semana de exámenes, ya que son varios?
3. ¿Qué fue lo más difícil de organizar durante la semana de exámenes?
4. ¿Qué hiciste cuando no pudiste cumplir tu plan de estudio?
5. ¿Cuánto tiempo aproximadamente dedicaste a planificar y cuánto a estudiar?

### 1.2 Hallazgos

| # | Hallazgo | Descripción |
|---|----------|-------------|
| H1 | Procrastinación por dificultad | Los estudiantes aplazan los cursos más complejos; una de las causas es no saber cómo calcular sus tiempos, lo que genera distracciones y preparación de último minuto. |
| H2 | Uso ineficaz de técnicas de estudio | Aunque intentan usar técnicas como Pomodoro, las aplican de manera obsoleta e interrumpen constantemente sus ciclos de concentración. |
| H3 | Exceso de confianza en cursos "fáciles" | Postergan intencionalmente los cursos que consideran fáciles hasta el último momento y se quedan sin margen si resultan necesitar más tiempo y repaso. |
| H4 | Estrés por exámenes continuos | Descansos de apenas 15 minutos entre exámenes de un mismo día impiden pausas adecuadas y generan estrés al intentar repasar contrarreloj. |
| H5 | Sacrificio por fatiga | Al fracasar su plan inicial recurren a las amanecidas como única solución: sesiones de estudio insuficientes y dominadas por el agotamiento. |

### 1.3 Oportunidades

| # | Oportunidad | Hallazgo que atiende |
|---|-------------|----------------------|
| O1 | Ayudar a los estudiantes a vencer la resistencia inicial a empezar a estudiar temas complejos. | H1 |
| O2 | Guiar a los alumnos para que respeten los tiempos exactos de concentración y descanso sin romper el método. | H2 |
| O3 | Prevenir que los estudiantes se queden sin tiempo de contingencia por aplazar demasiado los cursos que sí dominan. | H3 |
| O4 | Optimizar descansos ultracortos (como los 15 minutos) entre exámenes consecutivos para que el alumno repase sin abrumarse. | H4 |
| O5 | Evitar el agotamiento garantizando horas mínimas de sueño para que el estudiante retenga la información estudiada. | H5 |

### 1.4 Hipótesis del experimento

> Creemos que si reunimos en una sola planificación semanal los exámenes, laboratorios, trabajos y proyectos del estudiante, podrá visualizar toda su carga académica, identificar sus pendientes más urgentes y evitar olvidar entregas durante la semana de exámenes.

### 1.5 Backlog inicial (del experimento en papel)

1. Diseñar una plantilla física de planificación semanal con días, horas y espacios para registrar actividades.
2. Definir los datos que se registrarán por actividad: **curso, tipo, fecha, hora, tiempo estimado y prioridad**.
3. Preparar una ficha de instrucciones para que el estudiante registre toda su carga académica de la semana.
4. Seleccionar estudiantes con exámenes, laboratorios, trabajos o proyectos próximos.
5. Crear una tabla para registrar resultados: actividades, prioridades identificadas y pendientes olvidados.

---

## 2. Visión de producto (Lean UX)

Fuente: `04_lean_ux.png` y `05_mapa_epicas.png`.

- **Visión:** ayudar a los estudiantes a organizar su carga académica durante la semana de exámenes, permitiéndoles planificar sus actividades, identificar prioridades y aprovechar mejor su tiempo.
- **Enunciado del mapa de épicas ("Estrés en Semana de Exámenes"):** conectar a la comunidad de Tecsup con recursos de salud mental, técnicas de estudio efectivas y espacios de pausa activa para sobrellevar la semana de exámenes de manera saludable.
- **Propósito del equipo:** aliviar la semana de exámenes de los estudiantes; estudiantes productivos.

| Grupo objetivo | Necesidades | Producto | Objetivos de negocio |
|----------------|-------------|----------|----------------------|
| Estudiantes de todas las carreras | Identificar actividades urgentes | "Aplicación web de planificación académica para los estudiantes durante la semana de exámenes" (**se construye como app Android nativa**, decisión del equipo) | Ingresos por suscripciones Premium con funciones avanzadas: recordatorios, alertas urgentes, reportes de progreso y plan de estudios |
| Estudiantes con alta carga académica | Organizar horario de estudio | | Ofrecer licencias a institutos y universidades para que brinden la plataforma a sus estudiantes y docentes |
| Estudiantes con dificultades para organizar su tiempo de estudio | Evitar olvidar exámenes | | Generar un reporte del estudiante y vender esa información |
| | Priorizar según fecha, dificultad e importancia | | Ingresos mediante publicidad |
| | Retroalimentación teórica constante | | |

Los objetivos de negocio son contexto de producto; ninguna historia de la base los implementa.

---

## 3. Equipo (Team Canvas)

Fuente: `03_team_canvas.png`.

| Persona | Rol |
|---------|-----|
| Piero | Product Owner |
| Gabriel | Scrum Master |
| Frank | Arquitecto |
| Josefa | UX/UI |
| Tomas | Q.A. |

- **Objetivos comunes:** ser independiente, trabajar en una empresa, salir del país.
- **Objetivos personales:** viajar fuera de Arequipa; crear una aplicación propia de salud; desarrollar un proyecto que beneficie al mundo; mejorar físicamente; trabajar con una segunda carrera en Latam; comprar un departamento; ser independiente y conocer nuevos países; trabajar en una buena empresa y ser un excelente desarrollador senior.
- **Valores:** humor, empatía, disciplina, puntualidad, resiliencia, respeto, honestidad.
- **Necesidades y experiencia:** apoyo mutuo, avisar problemas o retrasos, participación de todo el equipo, cumplir tareas a tiempo, respeto y confianza, comunicación constante.
- **Reglas y actividades:** comunicación efectiva, respetar opiniones, participación activa, trabajo equitativo, lluvia de ideas, compartir avances y dificultades.
- **Fortalezas y activos:** pensamiento crítico, responsables, experiencia, proactivos, creativos.
- **Debilidades y riesgos:** falta de experiencia, conflictos por malos entendidos, dificultad para organizar, distraídos.

---

## 4. Épicas y features

Fuente: `05_mapa_epicas.png`.

| Épica | Feature | Historias del mapa |
|-------|---------|--------------------|
| **E1. Gestión y visión académica** | F1. Registro de evaluaciones | Registrar exámenes, laboratorios y proyectos en un solo lugar · Asignar dificultad e importancia a cada evaluación para calcular su urgencia · Indicar la importancia del curso para priorizarlo |
| | F2. Calendario y vista semanal | Ver tareas en un calendario semanal · Filtrar por curso o tipo de actividad · Identificar cruces de exámenes |
| | F3. Control de estado de avance | Actualizar el estado de las evaluaciones · Editar o eliminar actividades ante cambios de último minuto · Editar fechas si un profesor reprograma |
| **E2. Plan de estudios y simulacros** | F4. Motor inteligente de priorización | Que el sistema analice dificultad y horas de los exámenes · Lista ordenada por urgencia · Que el examen más difícil sea la máxima prioridad automáticamente |
| | F5. Tiempo bien distribuido | Estimar horas de estudio · Identificar qué curso requiere más tiempo · Recibir sugerencias de distribución de horas libres |
| | F6. Simulacros | Hacer simulacros según el temario · Ver simulacros y errores anteriores · Ver promedios de resultados por curso |

El mapa lista 18 historias; hay 17 tarjetas detalladas (ver sección 8, punto 2).

---

## 5. Historias de usuario

Total: **17 tarjetas (87 puntos)** + **5 historias del Kanban** (sin puntos ni criterios).

Todas las tarjetas son "Como estudiante de Tecsup".

### 5.1 Tabla de historias: épica, feature, puntos y criterios de aceptación

| ID | Épica | Feature | Historia | Pts | Quiero / Para | Criterios de aceptación |
|----|-------|---------|----------|-----|---------------|-------------------------|
| HU-01 | E1 | F1 | Registro de Exámenes | 5 | Registrar mis exámenes especificando tipo de prueba, temario y peso en la nota / centralizar mis evaluaciones teóricas y tener control de su impacto en mi promedio final. | 1. Formulario con campos: Nombre del examen, Curso, Fecha/Hora, Aula, Porcentaje de nota final.<br>2. Menú desplegable con tipo de examen: Parcial, Final, Caso de Estudio.<br>3. Campo de texto multilínea para redactar o adjuntar el temario a evaluar.<br>4. Validación automática de ponderación (mayor a 0% y menor o igual a 100%). |
| HU-02 | E1 | F1 | Registro de Laboratorios | 4 | Registrar mis prácticas y guías de laboratorio especificando software, equipos necesarios y fecha de informe / preparar los requerimientos técnicos con anticipación y entregar los informes a tiempo. | 1. Campos: Número de guía/práctica, Nombre del laboratorio, Lista de software/equipos requeridos, Fecha límite del informe.<br>2. Checkbox interactivo para definir si la entrega es individual o en parejas/grupal.<br>3. Módulo para adjuntar la guía en archivo PDF. |
| HU-03 | E1 | F1 | Registro de Proyectos | 7 | Registrar mis proyectos académicos desglosando sus entregables por fases y rúbricas de evaluación / llevar un control detallado del avance por hitos de un proyecto complejo durante el ciclo. | 1. Campos: Nombre del proyecto, Nombre del grupo/integrantes, Fases (Avance 1, Avance 2, Entrega Final) con fechas independientes.<br>2. Tabla de rúbrica editable con criterios de evaluación y puntajes asignados por cada criterio.<br>3. Estado de avance por cada fase del proyecto (Pendiente, En Proceso, Entregado). |
| HU-04 | E1 | F1 | Asignación de Dificultad e Importancia | 3 | Asignar niveles de dificultad e importancia a cada evaluación / que el sistema calcule automáticamente la urgencia de estudio. | 1. Selector interactivo de dificultad en escala de 1 a 5 estrellas.<br>2. Selector de nivel de importancia del curso (Baja, Media, Alta).<br>3. Código de color automático según la dificultad (Verde = Fácil, Amarillo = Medio, Rojo = Difícil). |
| HU-05 | E1 | F2 | Vista de Calendario Semanal | 6 | Visualizar mis tareas y evaluaciones en un calendario semanal / entender rápidamente mi disponibilidad de tiempo y carga de trabajo diaria. | 1. Matriz semanal de 7 días (lunes a domingo) organizada por franjas horarias.<br>2. Tarjetas de eventos identificadas por color según tipo (Examen, Laboratorio, Proyecto).<br>3. Modal informativo rápido al hacer clic en cualquier evento. |
| HU-06 | E1 | F2 | Filtrado de Calendario | 4 | Filtrar la vista del calendario por curso o tipo de actividad / tener claridad sobre entregas específicas y enfocarme en una materia a la vez. | 1. Filtros por botones rápidos: "Todos", "Exámenes", "Laboratorios", "Proyectos".<br>2. Lista desplegable para seleccionar un curso en específico.<br>3. Actualización en pantalla sin necesidad de recargar la página. |
| HU-07 | E1 | F2 | Detección de Cruces de Exámenes | 5 | Que el sistema identifique y me alerte sobre cruces o evaluaciones el mismo día / anticipar mi preparación y tomar medidas preventivas con tiempo. | 1. Ícono de alerta de conflicto en el calendario cuando 2 o más evaluaciones coinciden el mismo día o rango horario.<br>2. Mensaje flotante indicando exactamente cuáles evaluaciones presentan cruce. |
| HU-08 | E1 | F3 | Actualización de Estado de Evaluaciones | 3 | Actualizar el estado de mis evaluaciones (Pendiente, En Proceso, Completado) / llevar un control preciso sobre mi avance académico. | 1. Selector de estado rápido en la tarjeta de cada evaluación.<br>2. Barra de progreso que muestre el porcentaje global completado en la semana. |
| HU-09 | E1 | F3 | Edición y Reprogramación de Evaluaciones | 4 | Editar o cambiar las fechas de mis evaluaciones si un profesor las reprograma / mantener mi programación académica actualizada ante cambios de último minuto. | 1. Botón "Editar" en el detalle de la evaluación.<br>2. Selector modal de fecha/hora con confirmación de cambio.<br>3. Reorganización automática de la evaluación en el calendario tras la modificación. |
| HU-10 | E2 | F4 | Análisis Automatizado de Prioridades | 8 | Que el sistema analice la dificultad, ponderación y horas faltantes de cada evaluación / obtener un cálculo automatizado de la prioridad de estudio. | 1. Algoritmo de cálculo de prioridad: Prioridad = (Dificultad x Peso) / TiempoRestante.<br>2. Asignación de un puntaje de prioridad cuantitativo único por cada evaluación pendiente. |
| HU-11 | E2 | F4 | Lista Ordenada por Urgencia | 4 | Ver una lista ordenada por urgencia de mis próximas evaluaciones / atacar en primer lugar las tareas y exámenes más críticos. | 1. Dashboard principal con panel de tareas ordenadas de mayor a menor urgencia.<br>2. Etiqueta con el tiempo restante exacto (ej.: "Faltan 1 día y 4 horas").<br>3. Botón de acceso directo al detalle de la tarea urgente. |
| HU-12 | E2 | F5 | Estimación de Horas de Estudio | 3 | *(La tarjeta repite por error el texto de HU-02; corregido por el PO, D-6.)* Estimar las horas de estudio de mis actividades / organizar mis horas libres. | 1. Campo numérico editable para ingresar "Horas requeridas de estudio".<br>2. Valor sugerido automáticamente por el sistema basándose en la dificultad elegida. |
| HU-13 | E2 | F5 | Identificación de Curso Crítico | 5 | Identificar qué curso requiere más dedicación de tiempo / evitar descuidar materias difíciles o con mayor peso en el ciclo. | 1. Gráfico de pastel o barras mostrando la distribución de horas estimadas por curso en la semana.<br>2. Alerta de sobresaturación si un curso requiere más del 40% del tiempo total de estudio. |
| HU-14 | E2 | F5 | Sugerencia de Horas Libres | 8 | Recibir sugerencias automáticas de distribución de mis horas libres / estudiar de manera equilibrada sin sobrecargar los días previos al examen. | 1. Generación de una propuesta de cronograma que reparte bloques de estudio en los espacios libres.<br>2. Botón "Aceptar y agendar" para consolidar los bloques de estudio automáticamente en el calendario. |
| HU-15 | E2 | F6 | Realización de Simulacros por Temario | 7 | Visualizar y realizar simulacros de examen adaptados al temario registrado / practicar y medir mis conocimientos bajo condiciones reales de prueba. | 1. Módulo de evaluación interactiva con cronómetro regresivo en vivo.<br>2. Banco de preguntas con opciones múltiples y preguntas de caso.<br>3. Finalización y guardado automático de la prueba al agotarse el tiempo. |
| HU-16 | E2 | F6 | Promedio de Resultados de Simulacros | 6 | Ver las notas promedio y mi progreso en los simulacros por curso / evaluar si me encuentro preparado para la semana de exámenes real. | 1. Panel de métricas con gráfico de evolución de calificaciones a lo largo del tiempo.<br>2. Porcentaje representativo del "Nivel de Preparación" (ej.: "85% Preparado para Examen"). |
| HU-17 | E2 | F6 | Revisión de Errores en Simulacros | 5 | Visualizar los errores cometidos en simulacros anteriores / repasar las respuestas incorrectas y reforzar mis puntos débiles. | 1. Pantalla de resultados post-simulacro desglosando preguntas correctas, incorrectas y no respondidas.<br>2. Texto explicativo con la solución fundamentada de cada pregunta fallada. |

### 5.2 Tareas por rol

| ID | Piero (Product Owner) | Josefa (UX/UI) | Frank (Arquitecto) | Tomas (Q.A.) | Gabriel (Scrum Master) |
|----|-----------------------|----------------|--------------------|--------------|------------------------|
| HU-01 | Definir las reglas de negocio y los campos requeridos para el registro de exámenes. | Diseñar los prototipos de alta fidelidad del formulario de registro de exámenes. | Diseñar el modelo de datos en base de datos y construir la API REST para el registro de exámenes. | Elaborar los casos de prueba y ejecutar la validación de límites numéricos en los porcentajes de exámenes. | Asignar la historia en el Sprint Backlog, moderar la estimación y monitorear el avance diario. |
| HU-02 | Validar con los estudiantes la estructura de información de las guías de laboratorio. | Diseñar la interfaz con listas de verificación de equipos y el componente de carga de archivos PDF. | Configurar el servicio de almacenamiento de archivos (S3/Cloud Storage) y la estructura DB de laboratorios. | Realizar pruebas de carga de archivos PDF corruptos o de tamaño excesivo. | Facilitar la resolución de bloqueos entre el diseño de interfaz y la integración con el servicio de archivos. |
| HU-03 | Establecer los criterios de aprobación para la creación de proyectos y el desglose de rúbricas. | Crear los componentes visuales para tablas de rúbricas dinámicas y tarjetas de integrantes. | Diseñar el esquema de base de datos relacional entre proyectos, entregables y criterios de rúbrica. | Validar la adición y eliminación dinámica de fases y criterios sin pérdida de información. | Hacer seguimiento del cumplimiento del flujo de trabajo y asegurar que se cumpla la Definition of Done. |
| HU-04 | Especificar las ponderaciones y valores de la escala de dificultad e importancia. | Diseñar las etiquetas de color y el selector visual de estrellas interactivas. | Crear el endpoint de actualización de metadatos de dificultad para las evaluaciones. | Verificar que los cambios de dificultad actualicen inmediatamente los distintivos de color. | Monitorear la velocidad del equipo durante la implementación de la historia. |
| HU-05 | Priorizar la vista de calendario semanal dentro de la roadmap del producto. | Diseñar el layout responsivo del calendario semanal y el pop-up de vista rápida. | Implementar la arquitectura y consultas de rango de fechas para cargar eventos eficientemente. | Validar la consistencia visual y de datos en pantallas de escritorio y dispositivos móviles. | Coordinar la sincronización entre UX/UI y Backend para evitar cuellos de botella. |
| HU-06 | Definir las combinaciones de filtros más útiles según la necesidad estudiantil. | Diseñar la barra de controles de filtrado y los estados activo/inactivo de los botones. | Desarrollar la lógica de filtrado de datos en backend/frontend para respuestas de baja latencia. | Realizar pruebas de combinación de filtros (filtrar por Curso + Tipo de actividad). | Asegurar que las pruebas de aceptación de la historia se ejecuten a tiempo. |
| HU-07 | Definir la regla de negocio para clasificar cuándo dos evaluaciones constituyen un cruce crítico. | Diseñar el ícono de advertencia y la ventana modal flotante de conflicto. | Desarrollar el algoritmo de comparación de horarios para detectar superposiciones de eventos. | Probar casos de prueba extremos con múltiples evaluaciones asignadas en la misma hora. | Gestionar cualquier impedimento técnico surgido en el desarrollo del algoritmo. |
| HU-08 | Definir los estados válidos del ciclo de vida de una evaluación. | Diseñar el selector interactivo de estados y la barra de progreso semanal. | Crear el endpoint PATCH para la actualización de estado de evaluaciones. | Probar que al cambiar el estado de una evaluación la barra de progreso se recalcule de inmediato. | Guiar la revisión diaria del estado de la historia en el tablero de trabajo. |
| HU-09 | Determinar los permisos y condiciones para la reprogramación de fechas. | Diseñar el modal de edición de datos y confirmación de cambios. | Desarrollar la función de edición en base de datos y la re-sincronización con el calendario. | Ejecutar pruebas de regresión asegurando que editar una fecha no borre las notas o datos previos. | Asegurar la correcta documentación del entregable al cierre de la iteración. |
| HU-10 | Definir y aprobar la fórmula matemática del motor inteligente con sus constantes. | Diseñar el indicador visual del puntaje de prioridad en la tarjeta de evaluación. | Implementar la arquitectura del motor de priorización lógica en el servidor. | Realizar pruebas unitarias e integrales del cálculo matemático bajo múltiples escenarios. | Organizar sesiones de sincronización técnica entre el PO y el Arquitecto. |
| HU-11 | Establecer el orden jerárquico de visualización en el dashboard principal. | Diseñar la vista de lista priorizada con temporizadores regresivos. | Construir la API REST que devuelva la lista ordenada según la puntuación del motor. | Probar el comportamiento del orden de la lista cuando una evaluación cambia de estado a completada. | Velar por el cumplimiento de los tiempos de desarrollo acordados para la historia. |
| HU-12 | Establecer los parámetros base para las horas de estudio sugeridas por defecto. | Diseñar el componente numérico interactivo (+ / -) para modificar las horas de estudio. | Adaptar el modelo de datos para persistir las estimaciones de horas por estudiante. | Validar los límites de entrada (prevenir valores negativos o textos en campos numéricos). | Supervisar la adherencia a la metodología Scrum durante la construcción de la tarea. |
| HU-13 | Definir el porcentaje umbral que activa la alerta de "Curso Crítico". | Diseñar las gráficas estadísticas y el distintivo visual de alerta de sobrecarga. | Desarrollar la consulta de agregación de datos para calcular el porcentaje de horas por curso. | Verificar que los gráficos se actualicen en tiempo real al agregar o borrar actividades. | Asegurar que las entregas cumplan estrictamente los criterios de calidad. |
| HU-14 | Validar la efectividad de la distribución propuesta con respecto a los horarios de clase. | Diseñar el modal "Plan Sugerido de Estudio" con opciones para aceptar o descartar. | Programar el algoritmo inteligente para la asignación óptima de huecos libres en la agenda. | Validar que las sugerencias de estudio no solapen con las horas de clases o exámenes. | Organizar la demostración de la funcionalidad frente a los stakeholders en la Sprint Review. |
| HU-15 | Definir el formato de evaluación, número de preguntas y tiempo límite de los simulacros. | Diseñar la pantalla de examen interactivo, selector de opciones y el reloj temporizador. | Construir el modelo del banco de preguntas y la API de procesamiento de respuestas del simulacro. | Probar el comportamiento del guardado automático al llegar el contador a cero. | Facilitar la eliminación de impedimentos técnicos durante la construcción de la función. |
| HU-16 | Definir el algoritmo de estimación del "Nivel de Preparación" del alumno. | Diseñar el dashboard estadístico con gráficos de tendencia histórica. | Construir las consultas y endpoints para el cálculo de promedios de rendimiento. | Comprobar la exactitud de los datos numéricos mostrados en el gráfico estadístico. | Dirigir la reunión de Retrospectiva al finalizar la épica completa. |
| HU-17 | Validar la estructura del reporte pedagógico y las explicaciones de respuesta. | Diseñar el reporte de resultados con código de colores (Verde = Correcta, Rojo = Incorrecta). | Desarrollar la consulta backend para recuperar el historial de intentos y sus justificaciones. | Verificar que los comentarios de corrección coincidan exactamente con la pregunta evaluada. | Coordinar que las tareas de QA se ejecuten en paralelo con el desarrollo del sprint. |

Las tareas hablan de "API REST", "endpoints", "servidor", "S3" y "recargar la página" porque se escribieron pensando en una web. En Senda se traducen así: endpoints y API REST → repositorios sobre Firestore + Room; lógica "en el servidor" → casos de uso en la capa `domain`; "sin recargar la página" → estado reactivo en Compose.

### 5.3 Historias del Kanban

Fuente: `02_kanban.png`. No tienen puntos, criterios ni tareas por rol.

| ID | Columna | Como estudiante quiero… | Para… | Relación con las tarjetas |
|----|---------|-------------------------|-------|---------------------------|
| KB-01 | To Do | Un formulario | Registrar mis cursos | Requisito previo de HU-01 a HU-03 (todas piden "Curso"). Campos en D-13 y D-9. |
| KB-02 | To Do | Recibir notificaciones | Recordar tareas pendientes | Sin tarjeta. Se implementa con WorkManager. Reglas en D-13. |
| KB-03 | Doing | Una agenda personal de estudio | Tener un control claro de cada tarea | Se cubre con HU-05 (calendario) y HU-14 (bloques de estudio). |
| KB-04 | Doing | Un recordatorio | Enterarme sobre mis exámenes, laboratorios o proyectos | Sin tarjeta. Anticipación de los avisos en D-13. |
| KB-05 | Done | Un cronómetro | Aplicar la técnica "Pomodoro" | Sin tarjeta. Atiende H2/O2. Duraciones en D-13. El "Done" era del sprint de diseño: se construye desde cero. |

---

## 6. Reglas de negocio

Tabla final: combina la base oficial con las decisiones del PO (sección 9). La columna "Origen" indica la tarjeta o la decisión (D-n) que respalda cada regla.

### 6.1 Entidades y campos

| Entidad | Campos | Origen |
|---------|--------|--------|
| Usuario | Sesión propia; `plan` = FREE o PREMIUM. Todos los usuarios son PREMIUM por la promoción de lanzamiento, también las cuentas creadas antes (se migran al iniciar sesión) | D-16, D-18 |
| Curso | Nombre, docente (opcional), importancia, color, horario de clases semanal (día, inicio, fin, aula) | KB-01, D-9, D-13 |
| Examen | Nombre, curso, fecha/hora de inicio, hora de fin (opcional), aula, peso (%), tipo (Parcial, Final, Caso de Estudio), temario (texto multilínea; PDF opcional) | HU-01, D-8, D-11 |
| Laboratorio | Número de guía/práctica, nombre, curso, software/equipos requeridos, fecha límite del informe, hora de fin (opcional), modalidad (individual o en parejas/grupal), guía en PDF, peso (%) | HU-02, D-1, D-8 |
| Proyecto | Nombre, curso, grupo/integrantes, fases con fecha y estado propios, rúbrica editable (criterio + puntaje), peso (%) | HU-03, D-1 |
| Común a toda evaluación | Dificultad, estado, horas requeridas de estudio. La importancia no se guarda en la evaluación: se hereda del curso | HU-04, D-2 |

### 6.2 Escalas, validaciones y estados

| Regla | Valor | Origen |
|-------|-------|--------|
| Tipos de evaluación | Examen, Laboratorio, Proyecto. Un "trabajo" se registra como Proyecto de una sola fase | D-14 |
| Fases de proyecto | Avance 1, Avance 2, Entrega Final por defecto; se pueden agregar o quitar (mínimo 1) | HU-03, D-14 |
| Peso | Porcentaje de la nota del curso, para los 3 tipos. Validación: 0 < peso ≤ 100. Por defecto 10% en laboratorios y proyectos | HU-01, D-1, D-4 |
| Suma de pesos del curso | Si supera 100% se muestra una advertencia, pero se permite guardar | D-4 |
| Dificultad | 1 a 5 estrellas | HU-04 |
| Color por dificultad | 1–2 Verde (Fácil), 3 Amarillo (Medio), 4–5 Rojo (Difícil) | HU-04, D-3 |
| Importancia | Baja, Media, Alta. Se define en el curso | HU-04, D-2 |
| Estado de evaluación | Pendiente, En Proceso, Completado | HU-08 |
| Estado de fase de proyecto | Pendiente, En Proceso, Entregado | HU-03 |
| Estado del proyecto (derivado) | Pendiente si ninguna fase empezó; En Proceso si alguna avanzó; Completado cuando todas están Entregadas | D-17 |
| Color por tipo en calendario | Un color pastel por tipo: Examen, Laboratorio, Proyecto | HU-05 |

### 6.3 Priorización

| Regla | Valor | Origen |
|-------|-------|--------|
| Fórmula | `Prioridad = (Dificultad x Peso) / TiempoRestante` | HU-10 |
| Dificultad | 1 a 5 | HU-04 |
| Peso | Porcentaje de la nota del curso, en puntos (30% = 30) | D-1 |
| TiempoRestante | Horas hasta la evaluación, mínimo 1 | Decisión del equipo |
| Importancia | No entra en la fórmula; solo desempata | D-1 |
| Desempate | 1) mayor dificultad, 2) mayor importancia del curso, 3) fecha más cercana | D-5 |
| Orden del dashboard | De mayor a menor prioridad; las Completadas salen de la lista | HU-11 |
| Etiqueta de tiempo | Tiempo restante exacto, p. ej. "Faltan 1 día y 4 horas" | HU-11 |

### 6.4 Calendario, cruces y edición

| Regla | Valor | Origen |
|-------|-------|--------|
| Vista | 7 días, de lunes a domingo, por franjas horarias | HU-05 |
| Filtros | Todos, Exámenes, Laboratorios, Proyectos + desplegable por curso; se combinan | HU-06 |
| Duración por defecto (sin hora de fin) | Examen 2 h, laboratorio 1 h, entrega de proyecto 0 h (es un plazo) | D-8 |
| Alerta ROJA "Cruce" | Dos evaluaciones se solapan en horario. Las entregas de proyecto no generan cruce | HU-07, D-8 |
| Alerta AMARILLA "Día cargado" | 2 o más evaluaciones el mismo día sin solaparse | HU-07, D-8 |
| Mensaje de alerta | Nombra exactamente las evaluaciones implicadas | HU-07 |
| Reprogramar | Selector de fecha/hora con confirmación; no borra notas ni datos previos | HU-09 |
| Eliminar | Dentro de HU-09, con diálogo de confirmación | D-5 |
| Progreso semanal | Porcentaje de evaluaciones de la semana en Completado; se recalcula al instante | HU-08 |

### 6.5 Tiempo de estudio y plan

| Regla | Valor | Origen |
|-------|-------|--------|
| Horas sugeridas por dificultad | 1★ = 2 h, 2★ = 4 h, 3★ = 6 h, 4★ = 8 h, 5★ = 10 h; editables | HU-12, D-7 |
| Campo de horas | Numérico con +/-; sin negativos ni texto | HU-12 |
| Curso crítico | Alerta si un curso supera el 40% del total de horas de estudio de la semana | HU-13 |
| Gráfico | Pastel o barras con la distribución semanal de horas por curso | HU-13 |
| Disponibilidad diaria | 08:00–22:00 por defecto, configurable | D-9 |
| Sueño protegido | 23:00–06:00: nunca se agenda estudio en esa franja | D-9 |
| Plan sugerido | Bloques de estudio en espacios libres, dentro de la disponibilidad y sin solapar clases ni evaluaciones; "Aceptar y agendar" o descartar | HU-14, D-9 |

### 6.6 Simulacros

| Regla | Valor | Origen |
|-------|-------|--------|
| Origen de las preguntas | Temario (texto) del examen; las genera Gemini | HU-15, D-11 |
| Formato | 10 preguntas y 20 minutos por defecto, configurables | D-10 |
| Tipos de pregunta | Opción múltiple y de caso (escenario + opción múltiple); calificación automática | HU-15, D-10 |
| Tiempo | Cronómetro regresivo; al llegar a cero finaliza y se guarda solo | HU-15 |
| Nota | De 0 a 20 (escala vigesimal) | D-10 |
| Resultado | Correctas (verde), incorrectas (rojo) y no respondidas; explicación fundamentada de cada fallo | HU-17 |
| Nivel de Preparación | Promedio ponderado de los 3 últimos simulacros del curso (0.5 el más reciente, 0.3, 0.2), en % | HU-16, D-10 |
| Métricas | Promedio por curso y gráfico de evolución de notas | HU-16 |

### 6.7 Recordatorios, Pomodoro y bienestar

| Regla | Valor | Origen |
|-------|-------|--------|
| Recordatorios por evaluación | 3 días, 1 día y 2 horas antes; configurables | KB-02, KB-04, D-13 |
| Resumen diario | Notificación a las 07:30 | D-13 |
| Pomodoro | 25 min de estudio / 5 de pausa; pausa larga de 15 min cada 4 ciclos; configurable | KB-05, D-13 |
| Pausa activa | Sugerida cada 2 Pomodoros | D-15 |
| Recordatorio de descanso | A las 22:30 | D-15 |
| Rutina entre exámenes | Rutina de 15 minutos cuando hay exámenes seguidos el mismo día | O4, D-15 |
| Pantalla de Bienestar | Recursos de salud mental validados por el PO; sin diagnósticos | D-15 |
| Check-in de estrés | Diario, de 1 a 5, en el dashboard | D-12 |

### 6.8 IA, archivos y datos

| Regla | Valor | Origen |
|-------|-------|--------|
| Usos de Gemini | Generar preguntas desde el temario (HU-15), explicar errores (HU-17) y dar consejos de estudio | Decisión del equipo |
| Entrada de los consejos | Estrés del check-in + carga calculada (prioridades y horas pendientes) | D-12 |
| Límite de los consejos | Nunca recomiendan amanecidas | D-15 |
| PDFs | Copia en el almacenamiento interno de la app y ruta en Room; no se suben a la nube. Firebase Storage queda para la fase opcional | D-11 |
| Datos del usuario | Bajo `users/{uid}/...` en Firestore | Decisión del equipo |
| Planes | Solo interfaz: pantalla "Planes" (Gratuito S/ 0 y Premium S/ 9.90 al mes, referencial) e insignia "Premium" en Inicio. Ninguna función se bloquea ni se limita por plan | D-18 |
| Fuera de alcance | Pagos de la suscripción Premium, límites del plan Gratuito, licencias y publicidad | D-16, D-18 |
| Excluido | Venta de información del estudiante (Ley 29733 de Protección de Datos Personales) | D-16 |

---

## 7. Ideas extra del compañero

Fuente: `docs/ideas_extra/` (4 imágenes; "idea 2" e "idea 3" son la misma captura). Prioridad secundaria.

Clasificación: **opcional** = compatible con la base, va a una fase opcional al final · **postergada** = fuera del alcance de esta versión · **descartada** = contradice la base o las decisiones tomadas.

| # | Idea | Clasificación | Motivo |
|---|------|---------------|--------|
| 1 | "Temporada de exámenes" (p. ej. semanas 1 a 4) que agrupa cursos, fechas y materiales | Opcional | Compatible: amplía el horizonte de la vista semanal sin cambiarla. La base trabaja por semana. |
| 2 | Subir materiales del curso (sílabos, diapositivas, apuntes, guías) y que la IA extraiga los temas; el estudiante revisa y corrige | Opcional | Extiende el campo "temario" de HU-01 y el PDF de HU-02. Requiere almacenamiento de archivos y lectura de documentos con Gemini. |
| 3 | Temario organizado por curso y semana, mostrando de qué archivo salió cada tema | Opcional | Depende de la idea 2. La base solo pide el temario como texto. |
| 4 | Evaluación breve de diagnóstico (qué sabe ya el estudiante) | Opcional | Es un simulacro corto; reutiliza HU-15. |
| 5 | Preguntas iniciales: cuánto tiempo puede estudiar y qué temas domina o le cuestan | Opcional | Aporta el dato de disponibilidad que HU-14 necesita y la base no define. |
| 6 | Plan semanal personalizado según exámenes, tiempo disponible y áreas débiles | Opcional | El plan ya existe en HU-14; lo opcional es alimentarlo con el diagnóstico. |
| 7 | Práctica con preguntas, pistas y explicaciones basadas en los materiales | Opcional | Preguntas y explicaciones ya están en HU-15 y HU-17. Lo nuevo son las pistas y el apoyo en archivos. |
| 8 | Ajuste automático del plan según respuestas (menos tiempo a lo dominado, repaso a lo fallado) | Opcional | Compatible con HU-14 y HU-16; requiere seguimiento por tema. |
| 9 | Mostrar "qué estudiar después" y por qué | Opcional | Encaja con los consejos de Gemini y con la lista por urgencia (HU-11). |
| 10 | Indicador por tema: por estudiar, en práctica, necesita repaso, dominado | Opcional | No reemplaza los estados de evaluación (Pendiente, En Proceso, Completado); sería un nivel adicional por tema. |
| 11 | Principio: los archivos del alumno no se usan para entrenar modelos; la IA solo los usa como fuente | Opcional | Compatible y sin costo de desarrollo; conviene adoptarlo como criterio de privacidad si se hace la idea 2. |
| 12 | Empezar como web adaptable (PWA) antes que app nativa | Descartada | Contradice la decisión tomada: app Android nativa con Kotlin y Compose. |
| 13 | Diapositivas generadas y resúmenes en audio | Postergada | El propio compañero las deja para después; no responden a ninguna historia de la base. |
| 14 | Integración con plataformas institucionales | Postergada | Fuera del alcance; no hay historia que lo pida. |
| 15 | Funciones sociales | Postergada | Fuera del alcance; no hay historia que lo pida. |
| 16 | Producto configurable por institución (cursos, ciclos, semanas, reglas propias) | Postergada | Se alinea con el objetivo de negocio de licencias, pero la base define un usuario individual con datos en `users/{uid}`. |
| 17 | Uso en colegios: cuentas de menores, permisos y privacidad | Descartada | El grupo objetivo de la base son estudiantes de Tecsup (educación superior). |
| 18 | Validar con estudiantes qué les cuesta más para decidir qué construir primero | Postergada | Es una actividad de investigación del equipo, no una función de la app. |

---

## 8. Contradicciones y datos faltantes (resueltos)

Lista original de lo detectado en la base. Todo quedó resuelto en la sección 9.

1. **Web vs. Android.** El Lean UX dice "aplicación web" y varias tareas hablan de API REST, servidor, S3, escritorio y recarga de página. Resuelto por decisión del equipo: app Android nativa.
2. **18 historias en el mapa, 17 tarjetas.** No tienen tarjeta propia: "indicar la importancia del curso" (parece fusionada en HU-04), "eliminar actividades" (el mapa dice "editar o eliminar"; HU-09 solo cubre editar) y "el examen más difícil sea la máxima prioridad automáticamente". A cambio, "registrar exámenes, laboratorios y proyectos" se dividió en tres tarjetas.
3. **HU-12 con texto copiado.** El "quiero/para" de Estimación de Horas de Estudio es el de Registro de Laboratorios.
4. **`Peso` en la fórmula.** HU-10 habla de "ponderación", pero solo el examen tiene porcentaje de nota; laboratorios y proyectos no. La importancia (Baja/Media/Alta) no aparece en la fórmula.
5. **Importancia: ¿del curso o de la evaluación?** HU-04 dice "a cada evaluación" en el enunciado y "del curso" en el criterio.
6. **Estrellas vs. colores.** 5 niveles de dificultad y 3 colores, sin regla de corte.
7. **Validación de ponderación.** No se aclara si el límite de 100% es por examen o por la suma de los exámenes del curso.
8. **Cruce.** "Mismo día o rango horario" son dos reglas distintas; la tarea del PO de definir el cruce "crítico" sigue abierta.
9. **Tipo "trabajos".** La hipótesis y el backlog inicial mencionan trabajos; las tarjetas y los filtros solo tienen Examen, Laboratorio y Proyecto.
10. **Kanban sin detalle.** Cursos, notificaciones, recordatorios y Pomodoro no tienen criterios, puntos ni tareas. Pomodoro figura como "Done".
11. **Horas sugeridas por dificultad.** No hay tabla de valores.
12. **Horario de clases y horas libres.** HU-14 exige no solapar con clases, pero ninguna historia registra el horario ni la disponibilidad. Tampoco se modela el sueño mínimo (O5).
13. **Simulacros.** Faltan número de preguntas, tiempo límite, escala de nota, fórmula del "Nivel de Preparación" y cómo se califican las preguntas de caso.
14. **Adjuntos.** HU-01 (temario) y HU-02 (PDF) piden adjuntar archivos; el stack decidido no incluye almacenamiento de archivos.
15. **Estrés.** La IA debe aconsejar según "prioridad y estrés", pero ninguna historia captura el nivel de estrés.
16. **Dos enunciados de producto.** El mapa de épicas habla de salud mental y pausas activas; el Lean UX habla de planificación. Ninguna historia cubre salud mental.
17. **Objetivos de negocio.** Premium, licencias, publicidad y venta de información del estudiante no tienen historias; la venta de datos choca con un modelo de datos privados por usuario.
18. **Estados distintos.** Evaluaciones usan "Completado"; fases de proyecto usan "Entregado".

---

## 9. Decisiones del PO

Respuestas del Product Owner a los vacíos de la sección 8. Complementan la base oficial y mandan sobre las ideas extra.

| ID | Tema | Decisión |
|----|------|----------|
| D-1 | Peso en la fórmula | Peso = porcentaje de la nota del curso, para los 3 tipos. Laboratorios y proyectos también tienen el campo "peso (%)", por defecto 10%. La importancia no entra en la fórmula; sirve como desempate. |
| D-2 | Importancia | Se guarda en el curso y cada evaluación la hereda. |
| D-3 | Estrellas y colores | 1–2 verde, 3 amarillo, 4–5 rojo. |
| D-4 | Validación de peso | 0 < peso ≤ 100 en cada evaluación. Si la suma de las evaluaciones de un curso pasa de 100%, se muestra una advertencia, pero se deja guardar. |
| D-5 | Eliminar y desempates | "Eliminar" va dentro de HU-09, con diálogo de confirmación. "El más difícil, máxima prioridad" ya lo cubre la fórmula. En empates: dificultad, luego importancia del curso, luego fecha más cercana. |
| D-6 | HU-12 | El enunciado correcto es "estimar las horas de estudio de mis actividades para organizar mis horas libres". |
| D-7 | Horas sugeridas | 1★ = 2 h, 2★ = 4 h, 3★ = 6 h, 4★ = 8 h, 5★ = 10 h. El estudiante puede editarlas. |
| D-8 | Cruces | Alerta ROJA "Cruce" si dos evaluaciones se solapan en horario. Alerta AMARILLA "Día cargado" si hay 2 o más el mismo día sin solaparse. La hora de fin es opcional; sin ella: examen 2 h, laboratorio 1 h, entregas de proyecto 0 h (son plazos y no generan cruce de horario). |
| D-9 | Horario y disponibilidad | El formulario de curso incluye el horario de clases semanal (día, inicio, fin, aula). Hay una disponibilidad diaria, por defecto 08:00–22:00. El sueño de 23:00 a 06:00 está protegido: nunca se agenda estudio ahí. |
| D-10 | Simulacros | 10 preguntas y 20 minutos por defecto (configurable). Nota de 0 a 20. Las preguntas de caso son escenario + opción múltiple, calificadas automáticamente. Nivel de Preparación = promedio ponderado de los 3 últimos simulacros del curso (pesos 0.5, 0.3, 0.2), en %. |
| D-11 | PDFs | Se guardan en el dispositivo: copia en el almacenamiento interno de la app y ruta en Room. El temario principal es el campo de texto. Firebase Storage queda para la fase opcional. |
| D-12 | Estrés | Check-in diario de 1 a 5 en el dashboard. La IA recibe ese valor y la carga calculada (prioridades y horas pendientes). |
| D-13 | Curso, recordatorios y Pomodoro | Curso: nombre, docente (opcional), importancia, color y horario de clases. Recordatorios: 3 días, 1 día y 2 horas antes (configurables) + resumen diario a las 07:30. Pomodoro: 25/5 con pausa larga de 15 min cada 4 ciclos, configurable. Se construye desde cero. |
| D-14 | Tipo "trabajos" | Se mantienen 3 tipos. Los trabajos se registran como Proyecto con una sola fase. |
| D-15 | Bienestar | Dentro del alcance, en versión ligera: Pomodoro, pausa activa cada 2 Pomodoros, recordatorio de descanso a las 22:30, rutina de 15 minutos entre exámenes seguidos y una pantalla de Bienestar con recursos de salud mental que validará el PO (sin diagnósticos). Los consejos de Gemini nunca recomiendan amanecidas. |
| D-16 | Objetivos de negocio | Fuera de esta versión. Solo queda el campo `plan` = FREE o PREMIUM en User. La venta de información del estudiante queda excluida (Ley 29733 de Protección de Datos Personales). |
| D-17 | Estado del proyecto | Se deriva de sus fases: Pendiente si ninguna empezó, En Proceso si alguna avanzó, Completado cuando las 3 están Entregadas. |
| D-18 | Planes | El modelo de negocio del Lean UX se muestra solo como interfaz: pantalla "Planes" con las tarjetas Gratuito (S/ 0) y Premium (S/ 9.90 al mes, referencial). Todos los usuarios son Premium por promoción de lanzamiento. Los pagos y los límites del plan Gratuito quedan para una versión futura. |

Datos del proyecto: `minSdk` 26 es correcto. Git y `.gitignore` se configuran en el siguiente paso.

### Supuestos de implementación (confirmados por el PO)

Detalles que las decisiones no cubren. El PO aprobó los 4 tal como están:

1. **Proyectos de una fase (D-14 y D-17).** *(Confirmado.)* El número de fases es variable, así que "las 3 Entregadas" se lee como "todas las fases Entregadas".
2. **Prioridad de un proyecto.** *(Confirmado.)* El TiempoRestante se calcula contra la próxima fase no entregada, y el peso es el del proyecto completo.
3. **Día cargado.** *(Confirmado.)* Las entregas de proyecto no generan Cruce, pero sí cuentan para la alerta amarilla de "Día cargado".
4. **Nivel de Preparación con menos de 3 simulacros.** *(Confirmado.)* Se usan los disponibles y los pesos se reescalan para sumar 1 (con dos: 0.625 y 0.375; con uno: 1).
