package com.irpro.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CheckResult {
    data class Ok(val server: String = "", val expiresAt: Long? = null, val info: String = "") : CheckResult()
    data class Error(val message: String) : CheckResult()
}

/** Fonctions pures (testées dans src/test). */
object PlaylistRules {
    /** "monserveur.com:8080/player_api.php?x" -> "http://monserveur.com:8080" */
    fun normalizeServer(input: String): String {
        var s = input.trim().replace(" ", "")
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        val m = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE).find(s)
        return (m?.value ?: s).trimEnd('/')
    }

    fun normalizeUrl(input: String): String {
        val s = input.trim().replace(" ", "")
        if (s.isEmpty()) return ""
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "http://$s"
    }

    /** Vrai si le début du fichier ressemble à une liste M3U. */
    fun looksLikeM3u(head: String): Boolean {
        val t = head.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return t.startsWith("#EXTM3U", true) || t.contains("#EXTINF", true)
    }

    /** Xtream donne "exp_date" en secondes (texte ou nombre, parfois null). */
    fun parseExpiry(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.times(1000)

    fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(ms))
}

object PlaylistCheck {
    private const val UA = "IRPROTV/1.0 (Android TV)"

    suspend fun checkXtream(server: String, user: String, pass: String): CheckResult = withContext(Dispatchers.IO) {
        val base = PlaylistRules.normalizeServer(server)
        if (base.isEmpty()) return@withContext CheckResult.Error("Entre l'adresse du serveur.")
        if (user.isBlank() || pass.isBlank()) return@withContext CheckResult.Error("Entre ton nom d'utilisateur et ton mot de passe.")
        val url = "$base/player_api.php?username=${enc(user.trim())}&password=${enc(pass.trim())}"
        val body = try {
            httpGet(url, 300_000)
        } catch (e: HttpException) {
            return@withContext CheckResult.Error(
                if (e.code == 401 || e.code == 403) "Identifiants refusés par le serveur."
                else "Le serveur a répondu avec une erreur (${e.code})."
            )
        } catch (e: Exception) {
            return@withContext CheckResult.Error("Serveur injoignable. Vérifie l'adresse et ta connexion internet.")
        }
        val ui = try {
            JSONObject(body).optJSONObject("user_info")
        } catch (e: Exception) {
            null
        } ?: return@withContext CheckResult.Error("Ce serveur ne répond pas comme un serveur Xtream. Vérifie l'adresse.")

        if (ui.optString("auth") != "1") return@withContext CheckResult.Error("Nom d'utilisateur ou mot de passe incorrect.")
        val status = ui.optString("status")
        val exp = PlaylistRules.parseExpiry(ui.optString("exp_date"))
        when {
            status.equals("Expired", true) -> CheckResult.Error("Ton abonnement est expiré.")
            status.equals("Banned", true) || status.equals("Disabled", true) ->
                CheckResult.Error("Ce compte est désactivé par le fournisseur.")
            else -> CheckResult.Ok(
                server = base,
                expiresAt = exp,
                info = if (exp != null) "Abonnement actif jusqu'au ${PlaylistRules.formatDate(exp)}" else "Abonnement actif",
            )
        }
    }

    suspend fun checkM3u(link: String): CheckResult = withContext(Dispatchers.IO) {
        val url = PlaylistRules.normalizeUrl(link)
        if (url.isEmpty()) return@withContext CheckResult.Error("Colle le lien de ta liste M3U.")
        val head = try {
            httpGet(url, 64_000)
        } catch (e: HttpException) {
            return@withContext CheckResult.Error(
                if (e.code == 401 || e.code == 403) "Accès refusé : vérifie le lien (identifiants dedans ?)."
                else if (e.code == 404) "Lien introuvable (erreur 404). Vérifie le lien."
                else "Le serveur a répondu avec une erreur (${e.code})."
            )
        } catch (e: Exception) {
            return@withContext CheckResult.Error("Lien injoignable. Vérifie le lien et ta connexion internet.")
        }
        if (PlaylistRules.looksLikeM3u(head)) CheckResult.Ok(info = "Liste M3U valide")
        else CheckResult.Error("Ce lien ne mène pas à une liste M3U.")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    class HttpException(val code: Int) : IOException("HTTP $code")

    /** GET simple qui suit les redirections (y compris http <-> https) et lit au plus maxBytes. */
    private fun httpGet(start: String, maxBytes: Int): String {
        var url = start
        repeat(6) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw HttpException(code)
                    url = URL(URL(url), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw HttpException(code)
                c.inputStream.use { input ->
                    val buf = ByteArray(8192)
                    val out = java.io.ByteArrayOutputStream()
                    while (out.size() < maxBytes) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    return out.toString("UTF-8")
                }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Trop de redirections")
    }
}
