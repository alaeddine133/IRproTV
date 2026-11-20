package com.irpro.tv.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Reçoit la réponse de l'installateur d'Android pendant une mise à jour. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (code == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android demande la confirmation : on ouvre sa fenêtre « Installer »
            @Suppress("DEPRECATION")
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirm) }
                .onFailure { Updater.status.value = "Impossible d'ouvrir l'installateur d'Android sur cette box." }
            return
        }
        Updater.status.value = Updater.explain(code, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
