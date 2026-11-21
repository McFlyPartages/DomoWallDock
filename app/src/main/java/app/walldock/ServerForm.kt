package app.walldock

import android.app.Activity
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/**
 * Formulaire d'accès à un serveur (Home Assistant ou Gladys) dans les réglages :
 * accès local / externe / les deux, HTTP ou HTTPS, IP en quatre cases (chiffres seulement,
 * points fixes), port, adresse externe en https.
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
    private val ipParts = ArrayList<EditText>()
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
        val parts = server.ip.split('.')
        for (i in 0 until 4) {
            val part = field(parts.getOrNull(i) ?: "", if (i == 0) "192" else "0", InputType.TYPE_CLASS_NUMBER)
            part.filters = arrayOf(InputFilter.LengthFilter(3))
            part.gravity = Gravity.CENTER
            ipParts.add(part)
            row.addView(part, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (i < 3) row.addView(separator("."))
        }
        // 3 chiffres saisis : case suivante ; effacement dans une case vide : case précédente
        ipParts.forEachIndexed { i, part ->
            part.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if ((s?.length ?: 0) == 3 && part.hasFocus()) (ipParts.getOrNull(i + 1) ?: portField).requestFocus()
                }
            })
            part.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN && part.text.isEmpty() && i > 0) {
                    ipParts[i - 1].requestFocus()
                    ipParts[i - 1].setSelection(ipParts[i - 1].text.length)
                    true
                } else {
                    false
                }
            }
        }
        row.addView(separator(":"))
        portField = field(server.port.toString(), server.defaultPort.toString(), InputType.TYPE_CLASS_NUMBER)
        portField.filters = arrayOf(InputFilter.LengthFilter(5))
        portField.gravity = Gravity.CENTER
        row.addView(portField, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
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
    private fun ip(): String = ipParts.joinToString(".") { it.text.toString().trim() }
    private fun port(): Int = portField.text.toString().trim().toIntOrNull() ?: server.defaultPort
    private fun external(): String {
        val text = externalField.text.toString().trim().trimEnd('/')
        return if (text == "https:" || text == "https://" || text.isEmpty()) "" else text
    }

    private fun hasLocal() = ipParts.any { it.text.isNotBlank() }

    /** Rien de saisi : plateforme non configurée. */
    fun isEmpty(): Boolean = !hasLocal() && external().isEmpty()

    /** Problème à signaler, ou null si tout est bon. */
    fun validate(): String? {
        val access = access()
        if (access != Prefs.ACCESS_EXTERNAL) {
            val parts = ipParts.map { it.text.toString().trim().toIntOrNull() }
            if (parts.any { it == null || it > 255 }) return tr(R.string.server_error_ip)
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
