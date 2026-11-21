package com.richi_mc.hackathon3b.core.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Comparte un archivo (p. ej. el JSON exportado) con ACTION_SEND vía FileProvider. */
fun compartirArchivo(context: Context, path: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val selector = Intent.createChooser(envio, null).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(selector)
}
