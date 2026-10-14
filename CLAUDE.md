# Senda

App Android del equipo **Fuerza Salvaje** (Tecsup) para sobrellevar la semana de exámenes: el estudiante registra cursos, exámenes, laboratorios y proyectos, los ve en un calendario semanal, recibe una lista ordenada por prioridad, un plan de horas de estudio, simulacros con retroalimentación de IA y apoyo ligero de bienestar.

## Fuentes de verdad (en orden de prioridad)

1. `docs/base/*.png`: base oficial. Manda sobre todo lo demás.
2. Decisiones del PO: sección 9 de `docs/CONTEXTO_PROYECTO.md` (D-1 a D-17). Resuelven los vacíos de la base.
3. Las decisiones técnicas de este archivo.
4. `docs/CONTEXTO_PROYECTO.md`: transcripción de la base (HU-01..HU-17, KB-01..KB-05) y tabla final de reglas de negocio (sección 6).
5. `docs/ideas_extra/`: ideas de un compañero, prioridad secundaria. Nunca reemplazan ni contradicen la base; las compatibles van a una fase opcional al final.

Si algo no está definido en estas fuentes, preguntar antes de inventar.

## Stack y decisiones técnicas

- Paquete `com.fuerzasalvaje.senda`. Kotlin + Jetpack Compose + Material 3. minSdk 26.
- App Android nativa, aunque el Lean UX diga "aplicación web". Las tareas que mencionan API REST, endpoints o servidor se traducen a repositorios y casos de uso.
- Clean Architecture + MVVM: `ui -> domain <- data`. `domain` es Kotlin puro, sin dependencias de Android ni Firebase.
- Hilt para inyección. Room como caché offline. WorkManager para recordatorios.
- Firebase Auth + Cloud Firestore. Los datos de cada usuario viven bajo `users/{uid}/...`. `User.plan` = FREE | PREMIUM (solo el campo).
- IA con Google Gemini, detrás de una interfaz `GeminiService` en `domain`, para poder implementarla con Firebase AI Logic (`firebase-ai`, recomendado) o con `com.google.ai.client.generativeai` (deprecado).
- `GEMINI_API_KEY` y `GEMINI_MODEL` van en `local.properties` y se leen con `BuildConfig`.
- PDFs: copia en el almacenamiento interno de la app y ruta en Room. Firebase Storage queda para la fase opcional.
- Diseño inspirado en Knowunity: limpio, tarjetas, colores pastel modernos, dashboard centrado en el avance, navegación por pestañas. Solo inspiración: no copiar su marca ni sus recursos.
- Git: repositorio en GitHub, una rama por fase, commit al terminar cada fase con build verde.

## Reglas de negocio clave

Detalle completo y origen de cada regla: sección 6 de `docs/CONTEXTO_PROYECTO.md`.

**Modelo**
- Curso: nombre, docente (opcional), importancia (Baja/Media/Alta), color, horario de clases (día, inicio, fin, aula).
- Tipos de evaluación: Examen, Laboratorio, Proyecto. Un "trabajo" es un Proyecto de una sola fase.
- Tipos de examen: Parcial, Final, Caso de Estudio.
- Peso (%) en los 3 tipos: 0 < peso ≤ 100; por defecto 10% en laboratorios y proyectos. Si la suma del curso pasa de 100%, advertir pero dejar guardar.
- Dificultad: 1 a 5 estrellas. Color: 1–2 verde, 3 amarillo, 4–5 rojo.
- La importancia vive en el curso; la evaluación la hereda.
- Estado de evaluación: Pendiente, En Proceso, Completado. Fase de proyecto: Pendiente, En Proceso, Entregado.
- Estado del proyecto, derivado de sus fases: Pendiente si ninguna empezó, En Proceso si alguna avanzó, Completado si todas están Entregadas.

**Prioridad**
- `Prioridad = (Dificultad x Peso) / TiempoRestante`, con TiempoRestante en horas y mínimo 1.
- La importancia no entra en la fórmula. Desempate: dificultad, luego importancia del curso, luego fecha más cercana.
- Lista de mayor a menor; las Completadas salen de la lista.

**Calendario**
- 7 días, de lunes a domingo, por franjas horarias. Filtros: Todos, Exámenes, Laboratorios, Proyectos, y por curso.
- Hora de fin opcional. Sin ella: examen 2 h, laboratorio 1 h, entrega de proyecto 0 h.
- Alerta ROJA "Cruce": dos evaluaciones se solapan en horario (las entregas de proyecto no generan cruce).
- Alerta AMARILLA "Día cargado": 2 o más evaluaciones el mismo día sin solaparse.
- Editar, reprogramar y eliminar (HU-09) piden confirmación; reprogramar no borra datos previos.

**Tiempo de estudio**
- Horas sugeridas: 1★=2 h, 2★=4 h, 3★=6 h, 4★=8 h, 5★=10 h; editables, sin negativos.
- Curso crítico: alerta si un curso supera el 40% del total de horas de estudio.
- Disponibilidad diaria 08:00–22:00 por defecto. Sueño 23:00–06:00 protegido: nunca se agenda estudio ahí.
- El plan sugerido no solapa clases ni evaluaciones.

**Simulacros**
- 10 preguntas y 20 minutos por defecto, configurables. Opción múltiple y casos (escenario + opción múltiple), calificación automática.
- Nota de 0 a 20. Al llegar el cronómetro a cero, finaliza y se guarda solo.
- Resultado: correctas (verde), incorrectas (rojo), no respondidas; explicación de cada fallo.
- Nivel de Preparación = promedio ponderado de los 3 últimos simulacros del curso (0.5, 0.3, 0.2), en %.

**Recordatorios, Pomodoro y bienestar**
- Recordatorios: 3 días, 1 día y 2 horas antes (configurables) + resumen diario a las 07:30.
- Pomodoro: 25/5, pausa larga de 15 min cada 4 ciclos, configurable. Pausa activa cada 2 Pomodoros.
- Recordatorio de descanso a las 22:30. Rutina de 15 minutos entre exámenes seguidos.
- Pantalla de Bienestar con recursos validados por el PO, sin diagnósticos.
- Check-in de estrés diario (1 a 5) en el dashboard.

**IA y datos**
- Gemini genera preguntas desde el temario, explica errores y da consejos según el estrés y la carga (prioridades y horas pendientes).
- Los consejos nunca recomiendan amanecidas.
- Fuera de alcance: Premium, licencias y publicidad. Excluida la venta de información del estudiante (Ley 29733).

## Reglas de trabajo

- No hardcodear ni mostrar en logs la API Key.
- No versionar `local.properties` ni `google-services.json`.
- UI en español; código (nombres, comentarios, commits) en inglés.
- Tests unitarios para la lógica de negocio (`domain`): prioridad y desempates, cruces, curso crítico, validaciones, estados derivados, Nivel de Preparación.
- Antes de terminar cada tarea, `./gradlew assembleDebug` debe compilar (en PowerShell: `.\gradlew.bat assembleDebug`).
- No hacer `git push` sin que el usuario lo pida.
