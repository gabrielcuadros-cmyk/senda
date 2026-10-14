package com.fuerzasalvaje.senda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fuerzasalvaje.senda.data.notifications.SendaNotifier
import com.fuerzasalvaje.senda.domain.model.NotificationTarget
import com.fuerzasalvaje.senda.ui.navigation.DeepLinks
import com.fuerzasalvaje.senda.ui.navigation.SendaRoot
import com.fuerzasalvaje.senda.ui.theme.SendaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch carries the target; a recreated activity already handled it.
        if (savedInstanceState == null) handleTarget(intent)
        enableEdgeToEdge()
        setContent {
            SendaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SendaRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTarget(intent)
    }

    /** A tapped notification says which screen to open (see SendaNotifier). */
    private fun handleTarget(intent: Intent?) {
        val target = NotificationTarget.decode(intent?.getStringExtra(SendaNotifier.EXTRA_TARGET)) ?: return
        intent?.removeExtra(SendaNotifier.EXTRA_TARGET)
        DeepLinks.open(target)
    }
}
