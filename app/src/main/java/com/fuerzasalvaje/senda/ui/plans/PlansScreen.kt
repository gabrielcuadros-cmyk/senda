package com.fuerzasalvaje.senda.ui.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.model.Plan
import com.fuerzasalvaje.senda.domain.usecase.auth.ObserveSessionUseCase
import com.fuerzasalvaje.senda.ui.components.Pill
import com.fuerzasalvaje.senda.ui.components.SendaCard
import com.fuerzasalvaje.senda.ui.theme.CardShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Business model of the Lean UX, interface only: no payments and no limits (D-18). */
private data class PlanOffer(
    val plan: Plan,
    val name: String,
    val price: String,
    val period: String?,
    val features: List<String>,
)

private val Offers = listOf(
    PlanOffer(
        plan = Plan.FREE,
        name = "Gratuito",
        price = "S/ 0",
        period = null,
        features = listOf(
            "Registro de cursos y evaluaciones",
            "Calendario con cruces",
            "Lista por prioridad",
            "Pomodoro y Bienestar",
            "1 recordatorio por evaluación",
            "2 simulacros con IA al día",
            "3 explicaciones con IA al día",
        ),
    ),
    PlanOffer(
        plan = Plan.PREMIUM,
        name = "Premium",
        price = "S/ 9.90",
        period = "al mes · precio referencial",
        features = listOf(
            "Todo lo del plan Gratuito",
            "Recordatorios completos",
            "Alertas urgentes",
            "Plan de estudios automático",
            "Reportes de progreso completos",
            "Simulacros y explicaciones con IA ilimitados",
        ),
    ),
)

@HiltViewModel
class PlansViewModel @Inject constructor(observeSession: ObserveSessionUseCase) : ViewModel() {
    val currentPlan: StateFlow<Plan> = observeSession()
        .map { it?.plan ?: Plan.LAUNCH }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Plan.LAUNCH)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(onBack: () -> Unit, viewModel: PlansViewModel = hiltViewModel()) {
    val currentPlan by viewModel.currentPlan.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LaunchBanner()
            Offers.forEach { offer -> PlanCard(offer, isCurrent = offer.plan == currentPlan) }
        }
    }
}

@Composable
private fun LaunchBanner() {
    SendaCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text(
                "Promoción de lanzamiento: las primeras cuentas de Senda tienen Premium gratis",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PlanCard(offer: PlanOffer, isCurrent: Boolean) {
    val premium = offer.plan == Plan.PREMIUM
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (premium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (isCurrent) BorderStroke(2.dp, accent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (premium) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = accent)
                    Spacer(Modifier.width(8.dp))
                }
                Text(offer.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (isCurrent) Pill("Tu plan actual", accent.copy(alpha = 0.18f))
            }
            Spacer(Modifier.height(8.dp))
            Text(offer.price, style = MaterialTheme.typography.headlineMedium)
            offer.period?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            offer.features.forEach { FeatureLine(it, if (premium) accent else MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(12.dp))
            if (premium) {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isCurrent) "Plan activo" else "Premium")
                }
            } else {
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Plan Gratuito") }
                Text(
                    "Los pagos estarán disponibles próximamente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FeatureLine(text: String, tint: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
