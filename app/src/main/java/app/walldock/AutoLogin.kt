package app.walldock

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Connexion automatique au tableau de bord avec le compte saisi dans les réglages.
 *
 * Home Assistant : quand la page de connexion (/auth/authorize) s'affiche, l'appli suit le
 * même parcours que la page (login_flow puis /auth/token) et range les jetons là où
 * l'interface de HA les cherche (localStorage « hassTokens », comme « Rester connecté »).
 * Ne marche pas avec l'authentification à deux facteurs : il reste alors la page de connexion.
 *
 * Gladys Assistant : quand la page /login s'affiche, l'appli appelle /api/v1/login et range
 * la réponse dans localStorage « user », comme le fait l'interface de Gladys.
 */
class AutoLogin(private val prefs: Prefs, private val onError: (String) -> Unit) {

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var lastAttempt = 0L

    /** À appeler à chaque page (chargement ou changement d'adresse dans la page). */
    fun onPage(web: WebView, url: String?) {
        if (url.isNullOrBlank()) return
        val page = Uri.parse(url)
        val gladys = prefs.isGladys
        val server = Uri.parse(prefs.serverUrl)
        if (page.host == null || !page.host.equals(server.host, ignoreCase = true)) return
        val user = if (gladys) prefs.gladysUser else prefs.haUser
        val password = if (gladys) prefs.gladysPassword else prefs.haPassword
        if (user.isBlank() || password.isEmpty()) return
        if (!gladys) {
            if (page.path?.startsWith("/auth/authorize") == true) login(web, page)
            return
        }
        if (page.path == "/login") {
            login(web, page)
            return
        }
        // Gladys passe à /login sans recharger la page, ce que la WebView ne signale pas
        // toujours : on regarde directement si la page a une session.
        web.evaluateJavascript("(function(){try{return localStorage.getItem('user')===null}catch(e){return false}})()") {
            if (it == "true") login(web, page)
        }
    }

    private fun login(web: WebView, page: Uri) {
        // Une tentative par minute au plus : identifiants refusés = pas de boucle
        val now = SystemClock.elapsedRealtime()
        if (lastAttempt != 0L && now - lastAttempt < 60_000) return
        lastAttempt = now
        val gladys = prefs.isGladys
        val user = if (gladys) prefs.gladysUser else prefs.haUser
        val password = if (gladys) prefs.gladysPassword else prefs.haPassword
        val origin = "${page.scheme}://${page.encodedAuthority}"
        val target = prefs.dashboardUrl
        executor.execute {
            val script = try {
                if (gladys) gladysScript(origin, user, password, target) else homeAssistantScript(origin, user, password, target)
            } catch (e: Exception) {
                main.post { onError(describe(e)) }
                return@execute
            }
            main.post { web.evaluateJavascript(script, null) }
        }
    }

    /**
     * Bouton « Tester la connexion » des réglages : vérifie l'adresse, le compte et le mot de
     * passe sans toucher à la page. [done] reçoit null si tout va bien, sinon le problème.
     */
    fun test(gladys: Boolean, address: String, user: String, password: String, done: (String?) -> Unit) {
        val base = Uri.parse(address.trim())
        if (base.scheme == null || base.encodedAuthority == null) {
            done(tr(R.string.error_url))
            return
        }
        val origin = "${base.scheme}://${base.encodedAuthority}"
        executor.execute {
            val result = try {
                if (gladys) gladysSession(origin, user, password) else homeAssistantCode(origin, user, password)
                null
            } catch (e: Exception) {
                describe(e)
            }
            main.post { done(result) }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> tr(R.string.autologin_unreachable, e.message ?: "")
        is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.NoRouteToHostException ->
            tr(R.string.autologin_unreachable, e.message ?: "")
        is javax.net.ssl.SSLException -> tr(R.string.autologin_tls, e.message ?: "")
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Parcours de connexion de HA jusqu'au code d'autorisation (sans créer de jeton). */
    private fun homeAssistantCode(origin: String, user: String, password: String): String {
        val clientId = "$origin/"
        val flow = postJson(
            "$origin/auth/login_flow",
            JSONObject()
                .put("client_id", clientId)
                .put("handler", org.json.JSONArray().put("homeassistant").put(JSONObject.NULL))
                .put("redirect_uri", "$origin/?auth_callback=1")
        )
        val flowId = flow.optString("flow_id")
        if (flowId.isEmpty()) throw IllegalStateException(tr(R.string.autologin_failed))
        val step = postJson(
            "$origin/auth/login_flow/$flowId",
            JSONObject().put("username", user).put("password", password).put("client_id", clientId)
        )
        if (step.optString("type") != "create_entry") {
            // Mauvais mot de passe (errors.base = invalid_auth) ou deuxième facteur demandé
            throw IllegalStateException(tr(if (step.optString("step_id") == "mfa") R.string.autologin_mfa else R.string.autologin_refused))
        }
        return step.optString("result")
    }

    private fun homeAssistantScript(origin: String, user: String, password: String, target: String): String {
        val clientId = "$origin/"
        val code = homeAssistantCode(origin, user, password)
        val form = "grant_type=authorization_code&code=" + URLEncoder.encode(code, "UTF-8") +
            "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
        val tokens = JSONObject(request("$origin/auth/token", form, "application/x-www-form-urlencoded"))
        if (tokens.optString("access_token").isEmpty()) throw IllegalStateException(tr(R.string.autologin_failed))
        tokens.put("hassUrl", origin).put("clientId", clientId)
        // « expires » est calculé dans la page, avec l'horloge du navigateur
        return "(function(){var t=" + tokens + ";t.expires=Date.now()+t.expires_in*1000;" +
            "localStorage.setItem('hassTokens',JSON.stringify(t));location.replace(" + JSONObject.quote(target) + ");})();"
    }

    private fun gladysSession(origin: String, user: String, password: String): JSONObject {
        val session = try {
            postJson("$origin/api/v1/login", JSONObject().put("email", user.trim().lowercase()).put("password", password))
        } catch (e: HttpError) {
            // 404 : compte inconnu, 403 : mauvais mot de passe, 429 : trop d'essais
            throw IllegalStateException(tr(if (e.code in 400..499) R.string.autologin_refused else R.string.autologin_failed))
        }
        if (session.optString("access_token").isEmpty()) throw IllegalStateException(tr(R.string.autologin_failed))
        return session
    }

    private fun gladysScript(origin: String, user: String, password: String, target: String): String {
        val session = gladysSession(origin, user, password)
        return "(function(){localStorage.setItem('user'," + JSONObject.quote(session.toString()) + ");" +
            "location.replace(" + JSONObject.quote(target) + ");})();"
    }

    private class HttpError(val code: Int) : Exception("HTTP $code")

    private fun postJson(url: String, body: JSONObject): JSONObject =
        JSONObject(request(url, body.toString(), "application/json"))

    private fun request(url: String, body: String, type: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", type)
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            // HA répond 200 même pour un mauvais mot de passe (le formulaire revient avec une erreur)
            if (code !in 200..299) throw HttpError(code)
            return text
        } finally {
            connection.disconnect()
        }
    }
}
