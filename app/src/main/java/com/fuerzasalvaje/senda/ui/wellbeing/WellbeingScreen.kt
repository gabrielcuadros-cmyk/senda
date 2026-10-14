package com.fuerzasalvaje.senda.ui.wellbeing

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fuerzasalvaje.senda.ui.components.SendaCard

/** D-15: wellbeing resources, without diagnoses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bienestar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SendaCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(WellbeingContent.INTRO, style = MaterialTheme.typography.bodyMedium)
                }
            }
            WellbeingContent.sections.forEach { WellbeingSectionCard(it) }
            ContactsCard()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun WellbeingSectionCard(
    section: WellbeingContent.Section,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    SendaCard(modifier.fillMaxWidth(), containerColor = containerColor) {
        Text(section.title, style = MaterialTheme.typography.titleMedium)
        Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        section.steps.forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ContactsCard() {
    val context = LocalContext.current
    SendaCard(Modifier.fillMaxWidth()) {
        Text("Si necesitas hablar con alguien", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pedir ayuda está bien. Estos servicios te pueden orientar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WellbeingContent.contacts.forEach { contact ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(contact.label, style = MaterialTheme.typography.titleSmall)
                    Text(contact.detail, style = MaterialTheme.typography.bodySmall)
                }
                if (contact.phone != null) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        // ACTION_DIAL only opens the dialer: no call permission needed.
                        try {
                            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${contact.phone}".toUri()))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(contact.phone)
                    }
                }
            }
        }
    }
}
