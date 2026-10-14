package com.fuerzasalvaje.senda.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Opens a PDF copied into internal storage (D-11) with the viewer the student has installed. */
fun openLocalPdf(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "El PDF ya no está en este teléfono. Vuelve a adjuntarlo.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay una app para abrir PDF en este teléfono.", Toast.LENGTH_LONG).show()
    }
}
