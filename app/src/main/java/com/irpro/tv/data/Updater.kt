package com.irpro.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.irpro.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String, val size: Long = 0)

/** Mise à jour automatique : vérifie la dernière version publiée sur GitHub, la télécharge et lance l'installation. */
object Updater {
    private const val API = "https://api.github.com/repos/alaeddine133/IRproTV/releases/latest"

    /** Renvoie la nouvelle version si elle est plus récente que celle installée, sinon null. */
    /**
     * Version installée : « box » (légère, 32 bits) ou complète. On reprend toujours la même,
     * sinon une box peu puissante ou presque pleine refuse l'installation.
     */
    private fun isBoxBuild(ctx: Context): Boolean = runCatching {
        java.util.zip.ZipFile(ctx.applicationInfo.sourceDir).use { z -> z.getEntry("lib/arm64-v8a/libvlc.so") == null }
    }.getOrDefault(Build.SUPPORTED_64_BIT_ABIS.isEmpty())

    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val box = isBoxBuild(ctx)
        runCatching {
            val c = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "IRPROTV")
            }
            val o = c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            c.disconnect()
            val tag = o.optString("tag_name")                      // ex. v1.0.12
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return@runCatching null
            val assets = o.optJSONArray("assets") ?: return@runCatching null
            var url = ""
            var size = 0L
            val wanted = if (box) "IRPRO-TV-box.apk" else "IRPRO-TV.apk"
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name")
                if (n == wanted) { url = a.optString("browser_download_url"); size = a.optLong("size"); break }
                if (n == "IRPRO-TV.apk" && url.isEmpty()) { url = a.optString("browser_download_url"); size = a.optLong("size") }
            }
            if (url.isEmpty() || code <= BuildConfig.VERSION_CODE) null
            else UpdateInfo(code, tag.removePrefix("v"), o.optString("body").take(400), url, size)
        }.getOrNull()
    }

    /** Télécharge l'APK (progression 0..1) puis renvoie le fichier. */
    suspend fun download(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        // Android 6 n'accepte pas les fichiers partagés « content:// » : dossier externe de l'appli
        val base = if (Build.VERSION.SDK_INT < 24) (ctx.getExternalFilesDir(null) ?: ctx.cacheDir) else ctx.cacheDir
        val dir = File(base, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "IRPRO-TV-${info.versionName}.apk")
        fun open(u: String) = (URL(u).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "IRPROTV")
        }
        var conn = open(info.apkUrl)
        var hops = 0
        while (conn.responseCode in 300..399 && hops++ < 6) {
            val loc = conn.getHeaderField("Location"); conn.disconnect(); conn = open(loc)
        }
        val total = conn.contentLength.toLong()   // Android 6 : pas de contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
        conn.disconnect()
        // Fichier incomplet (connexion coupée) : Android dirait « Application non installée »
        if (info.size > 0 && out.length() != info.size) { out.delete(); error("téléchargement incomplet") }
        out
    }

    /** Vrai si Android autorise déjà l'appli à installer des mises à jour. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Ouvre l'écran Android « Autoriser cette source » pour IR PRO TV. */
    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) runCatching {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Message à afficher après une installation refusée par Android (null = rien à signaler). */
    val status = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /**
     * Télécharge la mise à jour DIRECTEMENT dans l'installateur d'Android (pas de copie en plus :
     * deux fois moins de place nécessaire), puis demande la confirmation. Si Android refuse,
     * la vraie raison arrive dans [status] (place insuffisante, conflit…).
     */
    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        status.value = null
        val pi = ctx.packageManager.packageInstaller
        // Sessions d'installation abandonnées (essais précédents) : on libère leur place
        pi.mySessions.forEach { runCatching { pi.abandonSession(it.sessionId) } }
        // Anciens fichiers téléchargés : supprimés aussi
        runCatching { File(ctx.cacheDir, "update").listFiles()?.forEach { it.delete() } }
        runCatching { ctx.getExternalFilesDir(null)?.let { File(it, "update").listFiles()?.forEach { f -> f.delete() } } }

        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (info.size > 0) params.setSize(info.size)
        val id = pi.createSession(params)
        val session = pi.openSession(id)
        try {
            fun open(u: String) = (URL(u).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "IRPROTV")
            }
            var conn = open(info.apkUrl)
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 6) {
                val loc = conn.getHeaderField("Location"); conn.disconnect(); conn = open(loc)
            }
            val total = conn.contentLength.toLong().let { if (it > 0) it else info.size }
            var done = 0L
            conn.inputStream.use { input ->
                session.openWrite("base.apk", 0, if (total > 0) total else -1).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                    session.fsync(out)
                }
            }
            conn.disconnect()
            if (info.size > 0 && done != info.size) error("téléchargement incomplet")
            val flags = if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(ctx, UpdateReceiver::class.java)
            val sender = android.app.PendingIntent.getBroadcast(ctx, id, intent, flags or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(sender.intentSender)
            session.close()
        } catch (e: Throwable) {
            runCatching { session.abandon() }
            throw e
        }
    }

    /** Traduit la réponse d'Android en phrase claire. */
    fun explain(code: Int, raw: String?): String? = when (code) {
        android.content.pm.PackageInstaller.STATUS_SUCCESS -> null
        android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE ->
            "Pas assez de place sur la box. Libère de l'espace (désinstalle une appli inutile ou vide le cache des applis) puis réessaie."
        android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Une autre version d'IR PRO TV bloque l'installation (signature différente). Désinstalle-la une fois, puis réinstalle avec Downloader."
        android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Cette version n'est pas compatible avec cette box."
        android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée."
        android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation bloquée par la box (autorisation « sources inconnues » ?)."
        android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID -> "Fichier abîmé pendant le téléchargement. Réessaie."
        else -> "Installation refusée par Android" + (raw?.takeIf { it.isNotBlank() }?.let { " : $it" } ?: ".")
    }

    /** Lance l'installateur Android (l'utilisateur appuie sur « Installer »). */
    fun install(ctx: Context, apk: File): Boolean = runCatching {
        val uri = if (Build.VERSION.SDK_INT < 24) Uri.fromFile(apk) else FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
