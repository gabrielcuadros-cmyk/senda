package com.fuerzasalvaje.senda.ui.wellbeing

import com.fuerzasalvaje.senda.domain.model.SendaRules

/**
 * Wellbeing resources (D-15): light habits, no diagnoses.
 * Draft content pending PO validation; keep it general and never suggest staying up.
 */
object WellbeingContent {

    data class Section(val title: String, val subtitle: String, val steps: List<String>)

    data class Contact(val label: String, val detail: String, val phone: String?)

    const val INTRO =
        "Hábitos sencillos para cuidar tu energía en la semana de exámenes. " +
            "Senda no hace diagnósticos ni reemplaza la ayuda de un profesional."

    val breathing = Section(
        title = "Respiración cuadrada",
        subtitle = "1 minuto · para bajar la tensión antes de estudiar o rendir",
        steps = listOf(
            "Inhala por la nariz contando 4.",
            "Sostén el aire contando 4.",
            "Exhala despacio por la boca contando 4.",
            "Espera contando 4 y repite 4 veces.",
        ),
    )

    val activePause = Section(
        title = "Pausa activa",
        subtitle = "2–3 minutos · cada ${SendaRules.ACTIVE_PAUSE_EVERY_POMODOROS} Pomodoros",
        steps = listOf(
            "Ponte de pie y gira los hombros hacia atrás 10 veces.",
            "Inclina la cabeza a cada lado y mantén 15 segundos.",
            "Entrelaza las manos y estira los brazos hacia arriba 15 segundos.",
            "Mira algo lejano durante 20 segundos para descansar la vista.",
            "Camina un poco y toma agua.",
        ),
    )

    val betweenExams = Section(
        title = "Rutina entre exámenes",
        subtitle = "${SendaRules.BETWEEN_EXAMS_ROUTINE_MINUTES} minutos · cuando tienes exámenes seguidos",
        steps = listOf(
            "Minutos 1–5: suelta el examen anterior, respira e hidrátate. Come algo ligero si puedes.",
            "Minutos 6–10: repasa solo ideas clave del siguiente examen; no abras temas nuevos.",
            "Minutos 11–15: camina, estírate y llega con calma al aula.",
        ),
    )

    val sleep = Section(
        title = "Descanso y sueño",
        subtitle = "El sueño también es parte del estudio",
        steps = listOf(
            "Senda protege tu horario de 23:00 a 06:00: nunca agenda estudio ahí.",
            "Evita amanecerte: dormir ayuda a fijar lo que estudiaste.",
            "A las 22:30 te recordamos cerrar el día. Deja las pantallas un rato antes de dormir.",
        ),
    )

    val sections = listOf(breathing, activePause, betweenExams, sleep)

    val contacts = listOf(
        Contact("Línea 113, opción 5", "Orientación en salud mental del MINSA. Gratuita.", "113"),
        Contact("SAMU 106", "Emergencias médicas.", "106"),
        Contact("Bienestar estudiantil", "Acércate al área de bienestar de tu sede Tecsup.", null),
    )
}
