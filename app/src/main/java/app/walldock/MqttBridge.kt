package app.walldock

import android.os.Handler
import android.os.Looper
import app.walldock.mqtt.Discovery
import app.walldock.mqtt.MqttClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Passerelle MQTT pour Gladys Assistant (et tout ce qui comprend la « découverte MQTT » de
 * Home Assistant) : la tablette s'annonce toute seule sur le broker, publie batterie, charge,
 * mouvement et état de l'écran, et reçoit les commandes (écran, caméra partagée, recharger,
 * tableau de bord).
 *
 * Dans Gladys : Intégrations → MQTT → appareils découverts (Home Assistant), la tablette
 * apparaît sous le nom « DomoWallDock … ».
 *
 * Gestion de la charge : « Charge demandée » passe à ON sous le seuil bas et à OFF au-dessus du
 * seuil haut ; une scène Gladys allume ou éteint la prise connectée du chargeur. Si la tablette
 * disparaît du réseau, le broker publie ON à sa place (testament MQTT) : la prise se rallume et la
 * batterie ne se vide pas.
 */
class MqttBridge(
    private val deviceId: String,
    private val deviceLabel: String,
    private val model: String,
    private val commands: Commands
) {

    interface Commands {
        fun setScreen(on: Boolean)
        fun reload()
        fun showDashboard()
        fun setCameraShare(on: Boolean)
    }

    private class Item(
        val component: String,
        val key: String,
        val name: Int,
        val deviceClass: String? = null,
        val unit: String? = null
    )

    private val items = listOf(
        Item("sensor", BATTERY, R.string.entity_batterie, "battery", "%"),
        Item("binary_sensor", CHARGING, R.string.entity_en_charge, "battery_charging"),
        Item("sensor", BATTERY_TEMPERATURE, R.string.entity_battery_temp, "temperature", "°C"),
        Item("binary_sensor", CHARGE_WANTED, R.string.entity_charge_wanted),
        Item("binary_sensor", MOTION, R.string.entity_motion, "motion"),
        Item("switch", SCREEN, R.string.entity_ecran),
        Item("switch", CAMERA_SHARE, R.string.entity_camera_share),
        Item("button", RELOAD, R.string.entity_recharger),
        Item("button", DASHBOARD, R.string.entity_retour_tableau_de_bord)
    )

    private val base = Discovery.baseTopic(deviceId)
    private val main = Handler(Looper.getMainLooper())
    // Le réseau est interdit sur le fil principal : tout ce qui part vers le broker passe par ici
    private val sender = Executors.newSingleThreadExecutor { Thread(it, "walldock-mqtt-send").apply { isDaemon = true } }
    private val states = ConcurrentHashMap<String, String>()

    @Volatile
    private var client: MqttClient? = null
    private var signature = ""
    @Volatile
    private var chargeManaged = false

    /** État pour l'écran des réglages : off, connecting, connected ou error. */
    @Volatile
    var status = "off"
        private set

    /** Détail de la dernière erreur de connexion. */
    @Volatile
    var lastError = ""
        private set

    /** Démarre, redémarre ou arrête la connexion selon les réglages. */
    fun apply(prefs: Prefs) {
        val wanted = prefs.mqttEnabled && prefs.mqttHost.isNotBlank()
        val newSignature = if (!wanted) "" else
            listOf(prefs.mqttHost, prefs.mqttPort, prefs.mqttUser, prefs.mqttPassword, prefs.chargeEnabled).joinToString("\u0000")
        if (newSignature == signature) return
        signature = newSignature
        stopClient()
        chargeManaged = prefs.chargeEnabled
        if (!chargeManaged) states.remove(CHARGE_WANTED)
        if (!wanted) {
            status = "off"
            return
        }
        val options = MqttClient.Options()
        options.host = prefs.mqttHost.trim()
        options.port = prefs.mqttPort
        options.username = prefs.mqttUser
        options.password = prefs.mqttPassword
        options.clientId = deviceId
        if (chargeManaged) {
            // Tablette injoignable : mieux vaut une prise allumée qu'une batterie à plat
            options.willTopic = "$base/$CHARGE_WANTED"
            options.willPayload = ON
            options.willRetain = true
        }
        status = "connecting"
        lateinit var created: MqttClient
        created = MqttClient(options, object : MqttClient.Listener {
            override fun onConnected() {
                if (client !== created) return
                status = "connected"
                lastError = ""
                announce(created)
            }

            override fun onDisconnected(reason: String) {
                if (client !== created) return
                status = "error"
                lastError = reason
            }

            override fun onMessage(topic: String, payload: String) = onCommand(topic, payload)
        })
        created.subscribe("$base/+/set")
        client = created
        created.start()
    }

    fun stop() {
        signature = ""
        stopClient()
        status = "off"
    }

    private fun stopClient() {
        val old = client ?: return
        client = null
        val giveBackCharge = chargeManaged
        sender.execute {
            // Arrêt volontaire : pas de testament, donc on redemande la charge nous-mêmes
            if (giveBackCharge) old.publish("$base/$CHARGE_WANTED", ON, true)
            old.stop()
        }
    }

    /** Nouvel état d'un capteur ; gardé en mémoire et republié à chaque reconnexion. */
    fun set(key: String, value: String) {
        if (key == CHARGE_WANTED && !chargeManaged) return
        if (states.put(key, value) == value) return
        val current = client ?: return
        sender.execute { current.publish("$base/$key", value, true) }
    }

    fun set(key: String, on: Boolean) = set(key, if (on) ON else OFF)

    /** Annonce des entités (découverte MQTT de Home Assistant, comprise par Gladys), puis les états. */
    private fun announce(target: MqttClient) {
        for (item in items) {
            val topic = Discovery.configTopic(item.component, deviceId, item.key)
            if (item.key == CHARGE_WANTED && !chargeManaged) {
                // Message vide : retire l'entité chez ceux qui l'avaient découverte
                target.publish(topic, "", true)
                target.publish("$base/$CHARGE_WANTED", "", true)
                continue
            }
            val config = Discovery.config(
                item.component, item.key, tr(item.name), item.deviceClass, item.unit,
                deviceId, deviceLabel, model, BuildConfig.VERSION_NAME
            )
            target.publish(topic, config, true)
        }
        for ((key, value) in states) target.publish("$base/$key", value, true)
    }

    private fun onCommand(topic: String, payload: String) {
        if (!topic.startsWith("$base/") || !topic.endsWith("/set")) return
        val key = topic.removePrefix("$base/").removeSuffix("/set")
        val value = payload.trim().uppercase()
        main.post {
            when (key) {
                SCREEN -> when (value) {
                    ON, "1", "TRUE" -> commands.setScreen(true)
                    OFF, "0", "FALSE" -> commands.setScreen(false)
                }
                CAMERA_SHARE -> when (value) {
                    ON, "1", "TRUE" -> commands.setCameraShare(true)
                    OFF, "0", "FALSE" -> commands.setCameraShare(false)
                }
                RELOAD -> commands.reload()
                DASHBOARD -> commands.showDashboard()
            }
        }
    }

    companion object {
        const val BATTERY = "battery"
        const val CHARGING = "charging"
        const val BATTERY_TEMPERATURE = "battery_temperature"
        const val CHARGE_WANTED = "charge_wanted"
        const val MOTION = "motion"
        const val SCREEN = "screen"
        const val CAMERA_SHARE = "camera_share"
        const val RELOAD = "reload"
        const val DASHBOARD = "dashboard"
        private const val ON = "ON"
        private const val OFF = "OFF"
    }
}

/** Charge par paliers : on recharge sous le seuil bas, on arrête au seuil haut, on ne change rien entre les deux. */
object ChargeControl {
    fun wanted(level: Int, low: Int, high: Int, previous: Boolean?): Boolean = when {
        level <= low -> true
        level >= high -> false
        else -> previous ?: true
    }
}
