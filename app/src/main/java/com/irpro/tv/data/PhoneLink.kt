package com.irpro.tv.data

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Ce que le téléphone a envoyé. */
data class PhoneForm(val type: String, val name: String, val server: String, val user: String, val pass: String, val url: String)

/**
 * Petit serveur web sur la box (réseau local uniquement) : le téléphone scanne le QR code,
 * remplit le formulaire, et la box reçoit les informations. Il s'arrête en quittant l'écran.
 */
class PhoneLink(private val onForm: suspend (PhoneForm) -> String?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private var job: Job? = null
    var address: String? = null; private set

    fun start(): String? {
        val ip = localIp() ?: return null
        val ss = runCatching { ServerSocket(8765) }.getOrElse { runCatching { ServerSocket(0) }.getOrNull() } ?: return null
        server = ss
        address = "http://$ip:${ss.localPort}"
        job = scope.launch {
            while (isActive) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                launch { runCatching { handle(s) }; runCatching { s.close() } }
            }
        }
        return address
    }

    fun stop() { runCatching { server?.close() }; job?.cancel() }

    private fun handle(s: Socket) {
        s.soTimeout = 15_000
        val input = s.getInputStream().buffered()
        val reqLine = readLine(input) ?: return
        var length = 0
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", true)) length = h.substringAfter(":").trim().toIntOrNull() ?: 0
        }
        val (method, path) = reqLine.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        val page = if (method == "POST" && path.startsWith("/add")) {
            val body = ByteArray(length.coerceIn(0, 20_000)).also { var r = 0; while (r < it.size) { val n = input.read(it, r, it.size - r); if (n < 0) break; r += n } }
            val f = parse(String(body, Charsets.UTF_8))
            val form = PhoneForm(f["type"] ?: "xtream", f["name"].orEmpty(), f["server"].orEmpty(), f["user"].orEmpty(), f["pass"].orEmpty(), f["url"].orEmpty())
            val error = runBlocking { onForm(form) }
            if (error == null) page("✅ Envoyé !", "<p>Regarde ta TV : IR PRO TV charge ta playlist.</p>")
            else page("Oups", "<p class='err'>${esc(error)}</p><p><a href='/'>Réessayer</a></p>")
        } else page("Ajouter ta playlist", FORM)
        val bytes = page.toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }

    private fun readLine(i: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = i.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8000) return sb.toString()
        }
    }

    private fun parse(b: String) = b.split("&").mapNotNull {
        val k = it.substringBefore("=", ""); if (k.isEmpty()) null
        else URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(it.substringAfter("=", ""), "UTF-8")
    }.toMap()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")

    private fun page(title: String, body: String) = """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>IR PRO TV</title><style>
body{margin:0;font-family:system-ui,sans-serif;background:#0a1430;color:#fff;padding:22px}
h1{font-size:22px;margin:4px 0 18px}h1 b{color:#22d3ee}.tabs{display:flex;gap:8px;margin-bottom:14px}
.tabs label{flex:1;text-align:center;padding:10px;border-radius:12px;background:#12204a;border:1px solid #2a3a6a}
input[type=radio]{display:none}#x:checked~.tabs .lx,#m:checked~.tabs .lm{background:#7c3aed;border-color:#22d3ee}
.m3u{display:none}#m:checked~.m3u{display:block}#m:checked~.xt{display:none}
input[type=text],input[type=password],input[type=url]{width:100%;box-sizing:border-box;padding:13px;margin:6px 0 12px;border-radius:10px;border:1px solid #2a3a6a;background:#12204a;color:#fff;font-size:16px}
button{width:100%;padding:15px;border:0;border-radius:30px;background:#22d3ee;color:#0a1430;font-size:17px;font-weight:700;margin-top:8px}
.err{color:#ff8a8a}a{color:#22d3ee}small{color:#9aa3b5}</style></head><body>
<h1>IR PRO <b>TV</b> · $title</h1>$body</body></html>"""

    companion object {
        private const val FORM = """<form method="post" action="/add">
<input type="radio" name="type" id="x" value="xtream" checked><input type="radio" name="type" id="m" value="m3u">
<div class="tabs"><label for="x" class="lx">Xtream Codes</label><label for="m" class="lm">Lien M3U</label></div>
<small>Nom de la playlist</small><input type="text" name="name" value="Ma TV">
<div class="xt"><small>Serveur</small><input type="url" name="server" placeholder="http://monserveur.com:8080" autocapitalize="off">
<small>Nom d'utilisateur</small><input type="text" name="user" autocapitalize="off" autocorrect="off">
<small>Mot de passe</small><input type="password" name="pass"></div>
<div class="m3u"><small>Lien de la liste M3U</small><input type="url" name="url" placeholder="http://…" autocapitalize="off"></div>
<button type="submit">Envoyer à la TV</button></form>
<p><small>Ton téléphone et la box doivent être sur le même Wi-Fi. Les informations vont directement à ta box.</small></p>"""

        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }.getOrNull()

        fun qr(text: String, size: Int = 512): Bitmap {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF0A1430.toInt() else 0xFFFFFFFF.toInt() }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
