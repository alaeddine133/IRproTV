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

data class UpdateInfo(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String)

/** Mise à jour automatique : vérifie la dernière version publiée sur GitHub, la télécharge et lance l'installation. */
object Updater {
    private const val API = "https://api.github.com/repos/alaeddine133/IRproTV/releases/latest"

    /** Renvoie la nouvelle version si elle est plus récente que celle installée, sinon null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
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
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
            }
            if (url.isEmpty() || code <= BuildConfig.VERSION_CODE) null
            else UpdateInfo(code, tag.removePrefix("v"), o.optString("body").take(400), url)
        }.getOrNull()
    }

    /** Télécharge l'APK (progression 0..1) puis renvoie le fichier. */
    suspend fun download(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
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
        val total = conn.contentLengthLong
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

    /** Lance l'installateur Android (l'utilisateur appuie sur « Installer »). */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
