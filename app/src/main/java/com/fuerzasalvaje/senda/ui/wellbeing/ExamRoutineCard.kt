package com.fuerzasalvaje.senda.ui.wellbeing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.engine.ExamRoutine
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.ui.components.Formats
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import java.time.ZoneId

/** D-15: today's back-to-back exams (15 to 60 min apart) and the 15-minute routine for the gap. Home and Focus. */
@Composable
fun ExamRoutineCard(routine: ExamRoutine, zone: ZoneId, modifier: Modifier = Modifier) {
    SendaCard(modifier.fillMaxWidth(), containerColor = SendaTheme.colors.busyDay.copy(alpha = 0.14f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SelfImprovement, contentDescription = null, tint = SendaTheme.colors.busyDay)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Hoy tienes exámenes seguidos", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${routine.finished.title} termina a las ${Formats.time(routine.finished.end, zone)} y " +
                        "${routine.next.title} empieza a las ${Formats.time(routine.next.start, zone)} " +
                        "(${routine.gap.toMinutes()} min). Usa la rutina de ${SendaRules.BETWEEN_EXAMS_ROUTINE_MINUTES} minutos:",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        WellbeingContent.betweenExams.steps.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
