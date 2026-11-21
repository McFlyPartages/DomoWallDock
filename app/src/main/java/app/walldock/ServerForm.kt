package app.walldock

import android.app.Activity
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/**
 * Formulaire d'accès à un serveur (Home Assistant ou Gladys) dans les réglages :
 * accès local / externe / les deux, HTTP ou HTTPS, IP (chiffres et points), port,
 * adresse externe en https.
 */
class ServerForm(
    private val activity: Activity,
    private val server: Prefs.Server,
    private val label: (String) -> TextView,
    private val field: (String, String, Int) -> EditText,
) {
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private lateinit var accessGroup: RadioGroup
    private lateinit var schemeGroup: RadioGroup
    private lateinit var ipField: EditText
    private lateinit var portField: EditText
    private lateinit var externalField: EditText
    private lateinit var localBlock: LinearLayout
    private lateinit var externalBlock: LinearLayout

    fun build(panel: LinearLayout) {
        panel.addView(label(tr(R.string.server_access)))
        accessGroup = radios(
            listOf(Prefs.ACCESS_LOCAL to R.string.server_access_local, Prefs.ACCESS_EXTERNAL to R.string.server_access_external,
                Prefs.ACCESS_BOTH to R.string.server_access_both),
            server.access
        )
        panel.addView(accessGroup)

        // Adresse locale : protocole, IP, port
        localBlock = LinearLayout(activity)
        localBlock.orientation = LinearLayout.VERTICAL
        localBlock.addView(label(tr(R.string.server_local)))
        schemeGroup = radios(listOf("http" to R.string.server_http, "https" to R.string.server_https), server.scheme)
        localBlock.addView(schemeGroup)
        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        // IP : un seul champ, chiffres et points seulement
        ipField = field(server.ip, "192.168.1.10", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        ipField.keyListener = DigitsKeyListener.getInstance("0123456789.")
        ipField.filters = arrayOf(InputFilter.LengthFilter(15))
        row.addView(ipField, LinearLayout.LayoutParams(0, WRAP_CONTENT, 3f))
        row.addView(separator(":"))
        portField = field(server.port.toString(), server.defaultPort.toString(), InputType.TYPE_CLASS_NUMBER)
        portField.filters = arrayOf(InputFilter.LengthFilter(5))
        portField.gravity = Gravity.CENTER
        row.addView(portField, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        localBlock.addView(row)
        panel.addView(localBlock)

        // Adresse externe : https obligatoire
        externalBlock = LinearLayout(activity)
        externalBlock.orientation = LinearLayout.VERTICAL
        externalBlock.addView(label(tr(R.string.server_external)))
        externalField = field(server.external, "https://", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        if (server.external.isEmpty()) externalField.setText("https://")
        externalBlock.addView(externalField)
        panel.addView(externalBlock)

        accessGroup.setOnCheckedChangeListener { _, _ -> showBlocks() }
        showBlocks()
    }

    private fun showBlocks() {
        val access = access()
        localBlock.visibility = if (access == Prefs.ACCESS_EXTERNAL) View.GONE else View.VISIBLE
        externalBlock.visibility = if (access == Prefs.ACCESS_LOCAL) View.GONE else View.VISIBLE
    }

    private fun access(): String = checked(accessGroup) ?: Prefs.ACCESS_LOCAL
    private fun scheme(): String = checked(schemeGroup) ?: "http"
    private fun ip(): String = ipField.text.toString().trim()
    private fun port(): Int = portField.text.toString().trim().toIntOrNull() ?: server.defaultPort
    private fun external(): String {
        val text = externalField.text.toString().trim().trimEnd('/')
        return if (text == "https:" || text == "https://" || text.isEmpty()) "" else text
    }

    private fun hasLocal() = ip().isNotEmpty()

    /** Rien de saisi : plateforme non configurée. */
    fun isEmpty(): Boolean = !hasLocal() && external().isEmpty()

    /** Problème à signaler, ou null si tout est bon. */
    fun validate(): String? {
        val access = access()
        if (access != Prefs.ACCESS_EXTERNAL) {
            val parts = ip().split('.').map { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it == null || it > 255 }) return tr(R.string.server_error_ip)
            if (port() !in 1..65535) return tr(R.string.server_error_port)
        }
        if (access != Prefs.ACCESS_LOCAL) {
            val url = external()
            if (!url.startsWith("https://") || android.net.Uri.parse(url).host.isNullOrBlank()) return tr(R.string.server_error_external)
        }
        return null
    }

    fun store() {
        server.access = access()
        server.scheme = scheme()
        server.ip = if (hasLocal()) ip() else ""
        server.port = port()
        server.external = external()
    }

    /** Adresses à tester : (libellé, adresse) pour l'accès choisi. */
    fun addresses(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        val access = access()
        if (access != Prefs.ACCESS_EXTERNAL && hasLocal()) {
            val implicit = (scheme() == "http" && port() == 80) || (scheme() == "https" && port() == 443)
            list.add(tr(R.string.server_access_local) to "${scheme()}://${ip()}" + if (implicit) "" else ":${port()}")
        }
        if (access != Prefs.ACCESS_LOCAL && external().isNotEmpty()) list.add(tr(R.string.server_access_external) to external())
        return list
    }

    private fun radios(choices: List<Pair<String, Int>>, current: String): RadioGroup {
        val group = RadioGroup(activity)
        group.orientation = RadioGroup.HORIZONTAL
        for ((value, text) in choices) {
            val choice = RadioButton(activity)
            choice.id = View.generateViewId()
            choice.text = tr(text)
            choice.tag = value
            choice.setTextColor(Color.WHITE)
            val spacing = RadioGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            spacing.marginEnd = dp(16)
            group.addView(choice, spacing)
            if (value == current) group.check(choice.id)
        }
        return group
    }

    private fun checked(group: RadioGroup): String? =
        group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag as? String

    private fun separator(text: String): TextView {
        val view = TextView(activity)
        view.text = text
        view.textSize = 20f
        view.setTextColor(Color.WHITE)
        view.setPadding(dp(2), 0, dp(2), 0)
        return view
    }
}
