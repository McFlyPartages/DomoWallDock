package app.walldock

import android.os.Handler
import android.os.Looper
import app.walldock.mqtt.MqttClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Boutons « Tester » des réglages : vérifient les valeurs saisies (même pas encore
 * enregistrées) sans toucher aux connexions en cours. [done] reçoit le message à afficher.
 */
object ConnectionTests {

    private val main = Handler(Looper.getMainLooper())

    /** Connexion MQTT d'essai, avec son propre identifiant (la connexion de l'appli reste en place). */
    fun mqtt(host: String, port: Int, user: String, password: String, done: (ok: Boolean, message: String) -> Unit) {
        val options = MqttClient.Options()
        options.host = host
        options.port = port
        options.username = user
        options.password = password
        options.clientId = "domowalldock-test-" + (System.currentTimeMillis() % 100_000)
        var finished = false
        lateinit var client: MqttClient
        fun finish(ok: Boolean, message: String) {
            synchronized(this) {
                if (finished) return
                finished = true
            }
            Thread { client.stop() }.start()
            main.post { done(ok, message) }
        }
        client = MqttClient(options, object : MqttClient.Listener {
            override fun onConnected() = finish(true, tr(R.string.test_mqtt_ok, "$host:$port"))
            override fun onDisconnected(reason: String) = finish(false, tr(R.string.test_failed, reason))
            override fun onMessage(topic: String, payload: String) {}
        })
        client.start()
        main.postDelayed({ finish(false, tr(R.string.test_failed, tr(R.string.test_timeout))) }, 12_000)
    }

    /** Clé d'API et album Immich : nom de l'album et nombre de photos. */
    fun immich(address: String, key: String, album: String, done: (ok: Boolean, message: String) -> Unit) {
        Thread {
            val (ok, message) = try {
                val base = address.trim().trimEnd('/')
                val id = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    .find(album.trim())?.value ?: album.trim()
                val (code, body) = get("$base/api/albums/$id?withoutAssets=true", key.trim())
                when {
                    code in 200..299 -> {
                        val json = JSONObject(body)
                        true to tr(R.string.test_immich_ok, json.optString("albumName"), json.optInt("assetCount"))
                    }
                    code == 401 || code == 403 -> false to tr(R.string.test_failed, tr(R.string.test_immich_key))
                    code == 400 || code == 404 -> false to tr(R.string.test_failed, tr(R.string.test_immich_album))
                    else -> false to tr(R.string.test_failed, "HTTP $code")
                }
            } catch (e: Exception) {
                false to tr(R.string.test_failed, e.message ?: e.javaClass.simpleName)
            }
            main.post { done(ok, message) }
        }.start()
    }

    private fun get(url: String, key: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("x-api-key", key)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}
